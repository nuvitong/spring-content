package internal.org.springframework.content.mongo.repository;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.UUID;

import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.commons.annotations.ContentLength;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;

import com.github.paulcwarren.ginkgo4j.Ginkgo4jRunner;
import com.mongodb.gridfs.GridFSFile;


@RunWith(Ginkgo4jRunner.class)
public class DefaultMongoStoreImplTest {
    private DefaultMongoStoreImpl<Object, String> mongoContentRepoImpl;
    private GridFsTemplate gridFsTemplate;
    private GridFSFile gridFSFile;
    private ContentProperty property;
    private GridFsResource resource;
    private ConversionService converter;

    private InputStream content;
    private InputStream result;

    {
        Describe("DefaultMongoStoreImpl", () -> {

        	BeforeEach(() -> {
            	converter = mock(ConversionService.class);
                gridFsTemplate = mock(GridFsTemplate.class);
                gridFSFile = mock(GridFSFile.class);
                resource = mock(GridFsResource.class);
                mongoContentRepoImpl = new DefaultMongoStoreImpl<Object, String>(gridFsTemplate, converter);
            });

            Context("#setContent", () -> {
                BeforeEach(() -> {
                    property = new TestEntity();

                    content = mock(InputStream.class);
                });

                JustBeforeEach(() -> {
                	mongoContentRepoImpl.setContent(property, content);
                });

                Context("when content is new", () -> {
                	BeforeEach(() -> {
                		when(converter.convert(isA(UUID.class), eq(String.class))).thenReturn("12345-67890");
                		when(gridFsTemplate.getResource(anyString())).thenReturn(null).thenReturn(resource);
//                		when(gridFsTemplate.store(anyObject(), anyString()));
                		when(resource.contentLength()).thenReturn(1L);
                	});

                	It("should use the mongoStoreConverter to find the resource path", () -> {
                		verify(converter).convert(isA(UUID.class), eq(String.class));
                	});

                	It("should store content in GridFS", () -> {
                		verify(gridFsTemplate).store(eq(content), eq("12345-67890"));
                	});

                	It("should update the content length", () -> {
                		assertThat(property.getContentLen(), is(1L));
                	});
            	});

                Context("#when the content already exists", () -> {
                	BeforeEach(() -> {
                		property.setContentId("abcd-efghi");

                		when(converter.convert(eq("abcd-efghi"), eq(String.class))).thenReturn("abcd-efghi");
                		when(gridFsTemplate.getResource(anyObject())).thenReturn(resource);
//                        when(gridFsTemplate.store(anyObject(), anyString()));
                		when(resource.exists()).thenReturn(true);
                		when(resource.contentLength()).thenReturn(1L);
                	});

                	It("should use the mongoStoreConverter to find the resource path", () -> {
                		verify(converter).convert(eq("abcd-efghi"), eq(String.class));
                	});

                	It("should delete the exsting resource", () -> {
                		verify(gridFsTemplate).delete(anyObject());
                	});

                	It("should store content in GridFS", () -> {
                		verify(gridFsTemplate).store(eq(content), eq("abcd-efghi"));
                	});

                	It("should update the content length", () -> {
                		assertThat(property.getContentLen(), is(1L));
                	});
                });
            });

             Context("#getContent", () -> {
                BeforeEach(() -> {
                    property = new TestEntity();
                    property.setContentId("abcd");

                    content = mock(InputStream.class);

            		when(converter.convert(eq("abcd"), eq(String.class))).thenReturn("abcd");
                    when(gridFsTemplate.getResource(anyObject())).thenReturn(resource);
                    when(resource.getInputStream()).thenReturn(content);
                });

                JustBeforeEach(() -> {
                	result = mongoContentRepoImpl.getContent(property);
                });

                Context("when the resource exists", () -> {
                    BeforeEach(() -> {
                        when(resource.exists()).thenReturn(true);
                    });

                    It("should use the converter to get the resource path", () -> {
                    	verify(converter).convert(eq("abcd"), eq(String.class));
                    });

                    It("should fetch the resource from that path", () -> {
                    	verify(gridFsTemplate).getResource("abcd");
                    });

                    It("should get content", () -> {
                        assertThat(result, is(content));
                    });
                });


                Context("when the resource does not exists", () -> {
                    BeforeEach(() -> {
                        when(resource.exists()).thenReturn(false);
                    });

                    It("should not find the content", () -> {
                        assertThat(result, is(nullValue()));
                    });
                });
            });

            Context("#unsetContent", () -> {
                BeforeEach(() -> {
                    property = new TestEntity();
                    property.setContentId("abcd");

            		when(converter.convert(eq("abcd"), eq(String.class))).thenReturn("abcd");
                    when(gridFsTemplate.getResource(anyObject())).thenReturn(resource);
                    when(resource.exists()).thenReturn(true);
                });

                JustBeforeEach(() -> {
                    mongoContentRepoImpl.unsetContent(property);
                });

                It("should use the converter to get the resource path", () -> {
                	verify(converter).convert(eq("abcd"), eq(String.class));
                });

                It("should fetch the resource from that path", () -> {
                	verify(gridFsTemplate).getResource(eq("abcd"));
                });

                It("should unset content", () -> {
                    verify(gridFsTemplate).delete(anyObject());
                });
                Context("when the property has a dedicated ContentId field", () -> {
                    It("should reset the metadata", () -> {
                        assertThat(property.getContentId(), is(nullValue()));
                        assertThat(property.getContentLen(), is(0L));
                    });
                });
                Context("when the property's ContentId field also is the javax persistence Id field", () ->{
                    BeforeEach(() -> {
                        property = new SharedIdContentIdEntity();
                        property.setContentId("abcd");
                    });
                    It("should not reset the content id metadata", () -> {
                        assertThat(property.getContentId(), is("abcd"));
                        assertThat(property.getContentLen(), is(0L));
                    });
                });
                Context("when the property's ContentId field also is the Spring Id field", () ->{
                    BeforeEach(() -> {
                        property = new SharedSpringIdContentIdEntity();
                        property.setContentId("abcd");
                    });
                    It("should not reset the content id metadata", () -> {
                        assertThat(property.getContentId(), is("abcd"));
                        assertThat(property.getContentLen(), is(0L));
                    });
                });
            });
        });
    }

    @Test
    public void test() {
    	//noop
    }

    public interface ContentProperty {
        String getContentId();
        void setContentId(String contentId);
        long getContentLen();
        void setContentLen(long contentLen);
    }

    public static class TestEntity implements ContentProperty {

        @ContentId
        private String contentId;

        @ContentLength
        private long contentLen;

        public TestEntity() {
            this.contentId = null;
        }

        public TestEntity(String contentId) {
            this.contentId = new String(contentId);
        }

        public String getContentId() { return this.contentId; }

        public void setContentId(String contentId) { this.contentId = contentId; }

        public long getContentLen() { return contentLen; }

        public void setContentLen(long contentLen) { this.contentLen = contentLen; }
    }

    public static class SharedIdContentIdEntity implements ContentProperty {

        @javax.persistence.Id
        @ContentId
        private String contentId;

        @ContentLength
        private long contentLen;

        public SharedIdContentIdEntity() {this.contentId = null;}

        public String getContentId() { return this.contentId; }

        public void setContentId(String contentId) { this.contentId = contentId; }

        public long getContentLen() { return contentLen; }

        public void setContentLen(long contentLen) { this.contentLen = contentLen; }
    }

    public static class SharedSpringIdContentIdEntity implements ContentProperty {

        @org.springframework.data.annotation.Id
        @ContentId
        private String contentId;

        @ContentLength
        private long contentLen;

        public SharedSpringIdContentIdEntity() { this.contentId = null; }

        public String getContentId() { return this.contentId; }

        public void setContentId(String contentId) { this.contentId = contentId; }

        public long getContentLen() { return contentLen; }

        public void setContentLen(long contentLen) { this.contentLen = contentLen; }
    }
}
