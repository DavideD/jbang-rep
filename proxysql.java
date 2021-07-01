///usr/bin/env jbang "$0" "$@" ; exit $?
/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */

//DEPS io.vertx:vertx-mysql-client:${vertx.version:4.1.0}
//DEPS io.vertx:vertx-unit:${vertx.version:4.1.0}
//DEPS org.hibernate.reactive:hibernate-reactive-core:${hibernate-reactive.version:1.0.0.CR6}
//DEPS org.assertj:assertj-core:3.19.0
//DEPS junit:junit:4.13.2
//DEPS org.testcontainers:mysql:1.15.3
//DEPS org.slf4j:slf4j-simple:1.7.30

//// Testcontainer needs the JDBC drivers to start the container
//// Hibernate Reactive doesn't need it
//DEPS mysql:mysql-connector-java:8.0.25

import java.time.LocalTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.provider.ReactiveServiceRegistryBuilder;
import org.hibernate.reactive.provider.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;

import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.testcontainers.containers.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

//DESCRIPTION An example of a JUnit test class for Hibernate Reactive using
//DESCRIPTION [Vert.x Unit](https://vertx.io/docs/vertx-unit/java),
//DESCRIPTION [Testcontainers](https://www.testcontainers.org)
//DESCRIPTION and [MySQL](https://www.mysql.com/)
//DESCRIPTION that you can run using [JBang](JBang).
//DESCRIPTION
//DESCRIPTION Before running the tests, Testcontainers will start the selected
//DESCRIPTION Docker image with the required database created.
//DESCRIPTION
//DESCRIPTION The `DATABASE` constant define which database to use and
//DESCRIPTION it can be change to any of the values in `Database`.
//DESCRIPTION
//DESCRIPTION Usage example:
//DESCRIPTION   1. Use as jbang template `jbang init -t mysql-reproducer@hibernate/hibernate-reactive mytest.java`
//DESCRIPTION   2. Run the test with JBang: `jbang mytest.java`
//DESCRIPTION   3. (Optional) Edit the file (with IntelliJ IDEA for example):
//DESCRIPTION             jbang edit --live --open=idea mytest.java
@RunWith(VertxUnitRunner.class)
public class proxysql {

	private Mutiny.SessionFactory sessionFactory;

	/**
	 * The {@link Configuration} for the {@link Mutiny.SessionFactory}.
	 */
	private Configuration createConfiguration() {
		Configuration configuration = new Configuration();

		// JDBC url
		configuration.setProperty( Settings.URL, "jdbc:mysql://localhost:3306/hreact" );

		// Credentials
		configuration.setProperty( Settings.USER, "hreact" );
		configuration.setProperty( Settings.PASS, "hreact" );

		// Schema generation. Supported values are create, drop, create-drop, drop-create, none
		configuration.setProperty( Settings.HBM2DDL_AUTO, "create" );

		// Register new entity classes here
		configuration.addAnnotatedClass( MyEntity.class );

		// (Optional) Log the SQL queries
		configuration.setProperty( Settings.SHOW_SQL, "true" );
		configuration.setProperty( Settings.HIGHLIGHT_SQL, "true" );
		configuration.setProperty( Settings.FORMAT_SQL, "true" );
		return configuration;
	}

	/*
	 * Create a new factory and a new schema before each test (see
	 * property `hibernate.hbm2ddl.auto`).
	 * This way each test will start with a clean database.
	 *
	 * The drawback is that, in a real case scenario with multiple tests,
	 * it can slow down the whole test suite considerably. If that happens,
	 * it's possible to make the session factory static and, if necessary,
	 * delete the content of the tables manually (without dropping them).
	 */
	@Before
	public void createSessionFactory() {
		Configuration configuration = createConfiguration();
		StandardServiceRegistryBuilder builder = new ReactiveServiceRegistryBuilder()
				.applySettings( configuration.getProperties() );
		StandardServiceRegistry registry = builder.build();

		sessionFactory = configuration.buildSessionFactory( registry )
				.unwrap( Mutiny.SessionFactory.class );
	}

	@Test
	public void testInsertAndSelect(TestContext context) {
		// the test will wait until async.complete or context.fail are called
		Async async = context.async();

		MyEntity entity = new MyEntity( "first entity", 1 );
		entity.time = LocalTime.of( 0, 0, 0 );

		sessionFactory
				// insert the entity in the database
				.withTransaction( (session, tx) -> session.persist( entity ) )
				.chain( () -> sessionFactory
						.withSession( session -> session
								// look for the entity by id
								.find( MyEntity.class, entity.getId() )
								// assert that the returned entity is the right one
								.invoke( foundEntity -> assertThat( foundEntity.time ).isEqualTo( LocalTime.of( 0,0,0,0 ) ) ) ) )
				.subscribe()
				.with( res -> async.complete(), context::fail );
	}

	@After
	public void closeFactory() {
		if ( sessionFactory != null ) {
			sessionFactory.close();
		}
	}

	/**
	 * Example of a class representing an entity.
	 * <p>
	 * If you create new entities, be sure to add them in .
	 * For example:
	 * <pre>
	 * configuration.addAnnotatedClass( MyOtherEntity.class );
	 * </pre>
	 */
	@Entity(name = "MyEntity")
	public static class MyEntity {
		@Id
		private Integer id;

		private String name;

		@Column(name = "time")
		public LocalTime time;

		public MyEntity() {
		}

		public MyEntity(String name, Integer id) {
			this.name = name;
			this.id = id;
		}

		public Integer getId() {
			return id;
		}

		public String getName() {
			return name;
		}

		@Override
		public String toString() {
			return "MyEntity"
					+ "\n\t id = " + id
					+ "\n\t name = " + name;
		}
	}

	// This main class is only for JBang so that it can run the tests with `jbang proxysql.java`
	public static void main(String[] args) {
		System.out.println( "Starting the test suite with MySQL");

		Result result = JUnitCore.runClasses( proxysql.class );

		for ( Failure failure : result.getFailures() ) {
			System.out.println();
			System.err.println( "Test " + failure.getTestHeader() + " FAILED!" );
			System.err.println( "\t" + failure.getTrace() );
		}

		System.out.println();
		System.out.print("Tests result summary: ");
		System.out.println( result.wasSuccessful() ? "SUCCESS" : "FAILURE" );
	}
}
