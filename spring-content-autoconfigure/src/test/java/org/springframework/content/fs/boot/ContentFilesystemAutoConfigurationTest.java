package org.springframework.content.fs.boot;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import org.junit.runner.RunWith;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.content.commons.annotations.Content;
import org.springframework.content.commons.annotations.ContentId;
import org.springframework.content.commons.repository.ContentStore;
import org.springframework.content.fs.io.FileSystemResourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;

import com.github.paulcwarren.ginkgo4j.Ginkgo4jConfiguration;
import com.github.paulcwarren.ginkgo4j.Ginkgo4jRunner;

import static com.github.paulcwarren.ginkgo4j.Ginkgo4jDSL.*;
import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import internal.org.springframework.content.fs.boot.autoconfigure.FilesystemContentAutoConfiguration;

@RunWith(Ginkgo4jRunner.class)
@Ginkgo4jConfiguration(threads=1)
public class ContentFilesystemAutoConfigurationTest {

	{
		Describe("FilesystemContentAutoConfiguration", () -> {
			Context("given a default configuration", () -> {
				It("should load the context", () -> {
					AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
					context.register(TestConfig.class);
					context.refresh();

					assertThat(context.getBean(TestEntityContentRepository.class), is(not(nullValue())));

					context.close();
				});
			});

			Context("given an environment specifying a filesystem root using spring prefix", () -> {
				BeforeEach(() -> {
					System.setProperty("SPRING_CONTENT_FS_FILESYSTEM_ROOT", "${java.io.tmpdir}/UPPERCASE/NOTATION/");
				});
				AfterEach(() -> {
					System.clearProperty("SPRING_CONTENT_FS_FILESYSTEM_ROOT");
				});
				It("should have a filesystem properties bean with the correct root set", () -> {
					AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
					context.register(TestConfig.class);
					context.refresh();

					assertThat(context.getBean(FilesystemContentAutoConfiguration.FilesystemProperties.class).getFilesystemRoot(), endsWith("/UPPERCASE/NOTATION/"));

					context.close();
				});
			});

            Context("given a configuration that contributes a loader bean", () -> {
				It("should have that loader bean in the context", () -> {
					AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
					context.register(ConfigWithLoaderBean.class);
					context.refresh();

					FileSystemResourceLoader loader = context.getBean(FileSystemResourceLoader.class);
					assertThat(loader.getFilesystemRoot(), is("/some/random/path/"));

					context.close();
				});
			});

		});
	}


	@Configuration
	@AutoConfigurationPackage
	@EnableAutoConfiguration
	public static class TestConfig {
	}

	@Configuration
	@AutoConfigurationPackage
	@EnableAutoConfiguration
	public static class ConfigWithLoaderBean {

		@Bean
		FileSystemResourceLoader fileSystemResourceLoader() {
			return new FileSystemResourceLoader("/some/random/path/");
		}

	}

	@Entity
	@Content
	public class TestEntity {
		@Id
		@GeneratedValue(strategy = GenerationType.AUTO)
		private long id;
		@ContentId
		private String contentId;
	}

	public interface TestEntityRepository extends JpaRepository<TestEntity, Long> {
	}

	public interface TestEntityContentRepository extends ContentStore<TestEntity, String> {
	}
}
