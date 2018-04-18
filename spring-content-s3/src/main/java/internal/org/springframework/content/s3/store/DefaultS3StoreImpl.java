package internal.org.springframework.content.s3.store;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.commons.annotations.ContentLength;
import org.springframework.content.commons.repository.ContentStore;
import org.springframework.content.commons.repository.Store;
import org.springframework.content.commons.repository.StoreAccessException;
import org.springframework.content.commons.utils.BeanUtils;
import org.springframework.content.commons.utils.Condition;
import org.springframework.content.s3.S3ContentIdHelper;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.WritableResource;
import org.springframework.util.Assert;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.DeleteObjectRequest;

public class DefaultS3StoreImpl<S, SID extends Serializable> implements Store<SID>, ContentStore<S,SID> {

	private static Log logger = LogFactory.getLog(DefaultS3StoreImpl.class);

	private ResourceLoader loader;
	private ConversionService converter;
	private AmazonS3 client;
	private S3ContentIdHelper<SID> contentIdHelper = S3ContentIdHelper.createDefaultS3ContentIdHelper();
	private String bucket;

	public DefaultS3StoreImpl(ResourceLoader loader, ConversionService converter, AmazonS3 client, String bucket) {
		Assert.notNull(loader, "loader must be specified");
		Assert.notNull(loader, "converter must be specified");
		Assert.notNull(loader, "client must be specified");
		this.loader = loader;
		this.converter = converter;
		this.client = client;
		this.bucket = bucket;
	}
	
	public S3ContentIdHelper<SID> getContentIdHelper() {
		return contentIdHelper;
	}

	public void setContentIdHelper(S3ContentIdHelper<SID> contentIdHelper) {
		this.contentIdHelper = contentIdHelper;
	}

	@Override
	public Resource getResource(SID id) {
		String bucket = this.contentIdHelper.getBucket(id, this.bucket);
		String objectId = this.contentIdHelper.getObjectId(id);

		if (bucket == null) {
			throw new StoreAccessException("Bucket not set");
		}

		String location = converter.convert(objectId, String.class);
		location = absolutify(bucket, location);
		Resource resource = loader.getResource(location);
		return resource;
	}

	@Override
	public void setContent(S property, InputStream content) {
		SID contentId = (SID) BeanUtils.getFieldWithAnnotation(property, ContentId.class);
		if (contentId == null) {
			UUID newId = UUID.randomUUID();
			contentId = (SID) converter.convert(newId, TypeDescriptor.forObject(newId), TypeDescriptor.valueOf(BeanUtils.getFieldWithAnnotationType(property, ContentId.class)));
			BeanUtils.setFieldWithAnnotation(property, ContentId.class, contentId);
		}
		this.contentIdHelper.validate(contentId);

		Resource resource = this.getResource(contentId);

		OutputStream os = null;
		try {
			if (resource instanceof WritableResource) {
				os = ((WritableResource)resource).getOutputStream();
				IOUtils.copy(content, os);
			}
		} catch (IOException e) {
			logger.error(String.format("Unexpected error setting content %s", contentId.toString()), e);
		} finally {
	        try {
	            if (os != null) {
	                os.close();
	            }
	        } catch (IOException ioe) {
	            // ignore
	        }
		}
			
		try {
			BeanUtils.setFieldWithAnnotation(property, ContentLength.class, resource.contentLength());
		} catch (IOException e) {
			logger.error(String.format("Unexpected error setting content length for content %s", contentId.toString()), e);
		}
	}

	@Override
	public InputStream getContent(S property) {
		if (property == null)
			return null;
		SID contentId = (SID)BeanUtils.getFieldWithAnnotation(property, ContentId.class);
		if (contentId == null)
			return null;

		Resource resource = this.getResource(contentId);
		try {
			if (resource.exists()) {
				return resource.getInputStream();
			}
		} catch (IOException e) {
			logger.error(String.format("Unexpected error getting content %s", contentId.toString()), e);
		}
		
		return null;
	}

	@Override
	public void unsetContent(S property) {
		if (property == null)
			return;
		SID contentId = (SID)BeanUtils.getFieldWithAnnotation(property, ContentId.class);
		if (contentId == null)
			return;

		// delete any existing content object
		try {
			deleteIfExists(contentId);

			// reset content fields
			BeanUtils.setFieldWithAnnotationConditionally(property, ContentId.class, null, new Condition() {
				@Override
				public boolean matches(Field field) {
					for (Annotation annotation : field.getAnnotations()) {
						if ("javax.persistence.Id".equals(annotation.annotationType().getCanonicalName()) ||
								"org.springframework.data.annotation.Id".equals(annotation.annotationType().getCanonicalName())) {
							return false;
						}
					}
					return true;
				}});
	        BeanUtils.setFieldWithAnnotation(property, ContentLength.class, 0);
		} catch (Exception ase) {
			logger.error(String.format("Unexpected error unsetting content %s", contentId.toString()), ase);
		}
	}

	private String absolutify(String bucket, String location) {
		String locationToUse = null;
		Assert.state(location.startsWith("s3://") == false);
		if (location.startsWith("/")) {
			locationToUse = location.substring(1);
		} else {
			locationToUse = location;
		}
		return String.format("s3://%s/%s", bucket, locationToUse);
	}
	
	private void deleteIfExists(SID contentId) {
		String bucketName = this.contentIdHelper.getBucket(contentId, this.bucket);
		
		Resource resource = this.getResource(contentId);
		if (resource.exists()) {
			client.deleteObject(new DeleteObjectRequest(bucketName, resource.getFilename()));
		}
	}
}
