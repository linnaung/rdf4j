/*******************************************************************************
 * Copyright (c) 2019 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/

package org.eclipse.rdf4j.sail.elasticsearchstore.benchmark;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.eclipse.rdf4j.common.transaction.IsolationLevels;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.sail.elasticsearchstore.ElasticsearchStore;
import org.eclipse.rdf4j.sail.elasticsearchstore.TestHelpers;
import org.eclipse.rdf4j.sail.extensiblestore.ExtensibleStore;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * @author Håvard Ottestad
 */
@State(Scope.Benchmark)
@Warmup(iterations = 20)
@BenchmarkMode({ Mode.AverageTime })
@Fork(value = 1, jvmArgs = { "-Xms8G", "-Xmx8G", "-Xmn4G", "-XX:+UseSerialGC" })
//@Fork(value = 1, jvmArgs = {"-Xms8G", "-Xmx8G", "-Xmn4G", "-XX:StartFlightRecording=delay=60s,duration=120s,filename=recording.jfr,settings=profile", "-XX:FlightRecorderOptions=samplethreads=true,stackdepth=1024", "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"})
@Measurement(iterations = 10)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class DeleteBenchmark {

	// @Param({ "100", "1000", "10000" })
	public int NUMBER_OF_STATEMENTS = 10000;

	private SailRepository elasticsearchStore;

	@Setup(Level.Trial)
	public void beforeClass() {
		// JMH does not correctly set JAVA_HOME. Change the JAVA_HOME below if you the following error:
		// [EmbeddedElsHandler] INFO p.a.t.e.ElasticServer - could not find java; set JAVA_HOME or ensure java is in
		// PATH
		TestHelpers.openClient();

		elasticsearchStore = new SailRepository(
				new ElasticsearchStore("localhost", TestHelpers.PORT, TestHelpers.CLUSTER, "testindex",
						ExtensibleStore.Cache.NONE));

		System.gc();

	}

	@TearDown(Level.Trial)
	public void afterClass() throws IOException {
		elasticsearchStore.shutDown();
		TestHelpers.closeClient();
	}

	@Setup(Level.Invocation)
	public void beforeInvocation() {

		try (SailRepositoryConnection connection = elasticsearchStore.getConnection()) {
			connection.begin(IsolationLevels.NONE);
			for (int i = 0; i < NUMBER_OF_STATEMENTS; i++) {
				connection.add(RDF.TYPE, RDFS.LABEL, SimpleValueFactory.getInstance().createLiteral(i));
			}
			connection.commit();
		}
	}

	@TearDown(Level.Invocation)
	public void afterInvocation() {

		try (SailRepositoryConnection connection = elasticsearchStore.getConnection()) {
			connection.begin(IsolationLevels.NONE);
			connection.clear();
			connection.commit();
		}
	}

	@Benchmark
	public boolean clearNone() {
		try (SailRepositoryConnection connection = elasticsearchStore.getConnection()) {
			connection.begin(IsolationLevels.NONE);
			connection.clear();
			connection.commit();
		}

		try (SailRepositoryConnection connection = elasticsearchStore.getConnection()) {
			return connection.hasStatement(RDFS.RESOURCE, RDF.TYPE, RDFS.RESOURCE, false, RDFS.RESOURCE);
		}

	}

	@Benchmark
	public boolean clearReadCommitted() {

		try (SailRepositoryConnection connection = elasticsearchStore.getConnection()) {
			connection.begin(IsolationLevels.READ_COMMITTED);
			connection.clear();
			connection.commit();
		}

		try (SailRepositoryConnection connection = elasticsearchStore.getConnection()) {
			return connection.hasStatement(RDFS.RESOURCE, RDF.TYPE, RDFS.RESOURCE, false, RDFS.RESOURCE);
		}

	}

	@Benchmark
	public boolean delete() {

		try (SailRepositoryConnection connection = elasticsearchStore.getConnection()) {
			connection.begin(IsolationLevels.NONE);
			connection.remove(RDF.TYPE, RDFS.LABEL, null);
			connection.commit();
		}

		try (SailRepositoryConnection connection = elasticsearchStore.getConnection()) {
			return connection.hasStatement(RDFS.RESOURCE, RDF.TYPE, RDFS.RESOURCE, false, RDFS.RESOURCE);
		}

	}

	@Benchmark
	public boolean deleteReadCommitted() {

		try (SailRepositoryConnection connection = elasticsearchStore.getConnection()) {
			connection.begin(IsolationLevels.READ_COMMITTED);
			connection.remove(RDF.TYPE, RDFS.LABEL, null);
			connection.commit();
		}

		try (SailRepositoryConnection connection = elasticsearchStore.getConnection()) {
			return connection.hasStatement(RDFS.RESOURCE, RDF.TYPE, RDFS.RESOURCE, false, RDFS.RESOURCE);
		}

	}

	@Benchmark
	public boolean hasStatement() {

		try (SailRepositoryConnection connection = elasticsearchStore.getConnection()) {
			return connection.hasStatement(RDFS.RESOURCE, RDF.TYPE, RDFS.RESOURCE, false, RDFS.RESOURCE);
		}

	}

}
