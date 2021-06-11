///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.hibernate.reactive:hibernate-reactive-core:1.0.0.CR6
//DEPS io.vertx:vertx-pg-client:4.1.0
//DEPS io.vertx:vertx-web:4.1.0
//DEPS com.fasterxml.jackson.core:jackson-databind:2.12.3
//DEPS ch.qos.logback:logback-classic:1.2.3
//DEPS org.testcontainers:postgresql:1.15.3

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.reactive.provider.ReactiveServiceRegistryBuilder;
import org.hibernate.reactive.provider.Settings;
import org.hibernate.reactive.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.testcontainers.containers.PostgreSQLContainer;

public class StageVerticle extends AbstractVerticle {

	private static final Logger logger = LoggerFactory.getLogger( StageVerticle.class );

	static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>( "postgres:11-alpine" )
			.withDatabaseName( "postgres" )
			.withUsername( "postgres" )
			.withPassword( "vertx-in-action" );

	private Stage.SessionFactory emf;

	private void startHibernate(Promise<Object> promise) {
		String jdbcUrl = config().getString( "jdbcUrl", "postgres://localhost:5432/postgres" );
		logger.info( "Connecting to: " + jdbcUrl );
		try {
			emf = createFactory( jdbcUrl ).unwrap( Stage.SessionFactory.class );
			promise.complete();
		}
		catch (Exception e) {
			promise.fail( e );
		}
	}

	public SessionFactory createFactory(String jdbcUrl) {
		Configuration configuration = new Configuration();

		// JDBC url
		configuration.setProperty( Settings.URL, postgreSQLContainer.getJdbcUrl() );

		// Credentials
		configuration.setProperty( Settings.USER, postgreSQLContainer.getUsername() );
		configuration.setProperty( Settings.PASS, postgreSQLContainer.getPassword() );

		configuration.setProperty( Settings.POOL_SIZE, "10");

		// Schema generation. Supported values are create, drop, create-drop, drop-create, none
		configuration.setProperty( Settings.HBM2DDL_AUTO, "create" );

		// Register new entity classes here
		configuration.addAnnotatedClass( Product.class );

		// (Optional) Log the SQL queries
		configuration.setProperty( Settings.SHOW_SQL, "true" );
		configuration.setProperty( Settings.HIGHLIGHT_SQL, "true" );
		configuration.setProperty( Settings.FORMAT_SQL, "true" );

		StandardServiceRegistryBuilder builder = new ReactiveServiceRegistryBuilder()
				.applySettings( configuration.getProperties() );
		StandardServiceRegistry registry = builder.build();

		return configuration.buildSessionFactory( registry );
	}

	@Override
	public void start(Promise<Void> promise) {
		final Future<Object> startHibernate = vertx.executeBlocking( this::startHibernate )
				.onComplete( objectAsyncResult -> {
					logger.info( "✅ Hibernate Reactive is ready" );
				} );

		BodyHandler bodyHandler = BodyHandler.create();

		Router router = Router.router( vertx );
		router.post().handler( bodyHandler );
		router.get( "/products" ).respond( this::listProducts );
		router.get( "/products/:id" ).respond( this::getProduct );
		router.post( "/products" ).respond( this::createProduct );

		final Future<HttpServer> startHttpServer = vertx.createHttpServer()
				.requestHandler( router )
				.listen( 8080 )
				.onSuccess( httpServer -> logger.info( "✅ HTTP server listening on port 8080" ) )
				.onFailure( err -> logger.error( "🔥 HTTP server not started", err ) );

		CompositeFuture.all( startHibernate, startHttpServer )
				.onSuccess( s -> promise.complete() )
				.onFailure( promise::fail );
	}

	@Override
	public void stop(Promise<Void> stopping) throws Exception {
		vertx.executeBlocking( promise -> {
			try {
				if ( emf != null ) {
					emf.close();
				}
				promise.complete();
			}
			catch (Exception e) {
				promise.fail( e );
			}
		} ).onComplete( asyncResult -> stopping.complete() );
	}

	private Future<List<Product>> listProducts(RoutingContext ctx) {
		return Future.fromCompletionStage( emf.withSession( session -> session
				.createQuery( "from Product", Product.class )
				.getResultList() ) );
	}


	private Future<Product> getProduct(RoutingContext ctx) {
		long id = Long.parseLong( ctx.pathParam( "id" ) );
		return Future.fromCompletionStage( emf.withSession( session -> session
				.find( Product.class, id )
		).thenApply( product -> product == null ? new Product() : product ) );
	}

	private Future<Product> createProduct(RoutingContext ctx) {
		final Product product = ctx.getBodyAsJson().mapTo( Product.class );
		return Future.fromCompletionStage( emf.withSession( session -> session
				.persist( product )
				.thenCompose( unused -> session.flush() )
				.thenApply( unused -> product ) )
		);
	}

	public static void main(String... args) {
		long startTime = System.currentTimeMillis();

		logger.info( "🚀 Starting a PostgreSQL container" );
		postgreSQLContainer.start();

		long tcTime = System.currentTimeMillis();
		Vertx vertx = Vertx.vertx();

		DeploymentOptions options = new DeploymentOptions()
				.setConfig( new JsonObject().put( "jdbcUrl", postgreSQLContainer.getJdbcUrl() ) );

		vertx.deployVerticle( StageVerticle::new, options )
				.onSuccess( s -> {
					long vertxTime = System.currentTimeMillis();
					logger.info( "✅ Deployment success" );
					logger.info( "💡 PostgreSQL container started in {}ms", ( tcTime - startTime ) );
					logger.info( "💡 Vert.x app started in {}ms", ( vertxTime - tcTime ) );
				} )
				.onFailure( err -> logger.error( "🔥 Deployment failure", err ) );
	}

	@Entity(name = "Product")
	public static class Product {

		@Id
		@GeneratedValue
		private Long id;

		@Column(unique = true)
		private String name;

		@Column(nullable = false)
		private BigDecimal price;

		public Product() {
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public BigDecimal getPrice() {
			return price;
		}

		public void setPrice(BigDecimal price) {
			this.price = price;
		}
	}
}
