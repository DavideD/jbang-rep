///usr/bin/env jbang "$0" "$@" ; exit $?
/* Hibernate, Relational Persistence for Idiomatic Java
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright: Red Hat Inc. and Hibernate Authors
 */

//DEPS io.vertx:vertx-mysql-client:${vertx.version:4.1.2}
//DEPS io.vertx:vertx-unit:${vertx.version:4.1.2}
//DEPS org.hibernate.reactive:hibernate-reactive-core:${hibernate-reactive.version:1.0.0.CR8}
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
import javax.persistence.GeneratedValue;
import javax.persistence.Id;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.provider.ReactiveServiceRegistryBuilder;
import org.hibernate.reactive.provider.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;

import io.smallrye.mutiny.Uni;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

//DESCRIPTION Usage example:
//DESCRIPTION   1. Use as jbang template `jbang init -t issue881@DavideD/jbang-rep Issue881.java`
//DESCRIPTION   2. Start docker images with: `docker-compose up`
//DESCRIPTION   2. Run the test with JBang: `jbang Issue881.java`
//DESCRIPTION   3. (Optional) Edit the file (with IntelliJ IDEA for example):
//DESCRIPTION             jbang edit --live --open=idea Issue881.java
@RunWith(VertxUnitRunner.class)
public class Issue881 {

	private Mutiny.SessionFactory sessionFactory;

	/**
	 * The {@link Configuration} for the {@link Mutiny.SessionFactory}.
	 */
	private Configuration createConfiguration() {
		Configuration configuration = new Configuration();

		// JDBC url
		configuration.setProperty( Settings.URL, "mysql://127.0.0.1:6033/hreact" );

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
		configuration.setProperty( Settings.FORMAT_SQL, "false" );
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
	public void testLocalTime(TestContext context) {
		// the test will wait until async.complete or context.fail are called
		Async async = context.async();

		sessionFactory.withTransaction( Issue881::createEntities )
				// Check if sometimes the time is saved with the wrong value
				.chain( () -> sessionFactory.withSession( session -> session
						.createQuery( "from MyEntity e where e.time!='00:00'" )
						.getResultList() ) )
				.invoke( list -> context.assertTrue( list.isEmpty() ) )
				.subscribe()
				.with( res -> async.complete(), context::fail );
	}

	private static Uni<Void> createEntities(Mutiny.Session session, Mutiny.Transaction tx) {
		Uni<Void> loop = Uni.createFrom().voidItem();
		for ( int i = 0; i < 1000; i++ ) {
			final MyEntity entity = new MyEntity();
			loop = loop.chain( () -> session.persist( entity )
					.invoke( () -> System.out.println( "Entity id: " + entity.getId() ) ) );
		}
		return loop;
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
		@GeneratedValue
		public Integer id;

		public String name;

		@Column(name = "time")
		private LocalTime time = LocalTime.of( 0, 0 );

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

	// This main class is only for JBang so that it can run the tests with `jbang Issue881.java`
	public static void main(String[] args) {
		System.out.println( "Starting the test suite with MySQL");

		Result result = JUnitCore.runClasses( Issue881.class );

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
