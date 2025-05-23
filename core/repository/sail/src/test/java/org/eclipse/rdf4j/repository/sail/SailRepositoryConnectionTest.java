/*******************************************************************************
 * Copyright (c) 2020 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.repository.sail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.eclipse.rdf4j.common.iteration.EmptyIteration;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.Query;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.algebra.QueryRoot;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.explanation.Explanation;
import org.eclipse.rdf4j.sail.SailConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SailRepositoryConnection}
 *
 * @author Jeen Broekstra
 */
public class SailRepositoryConnectionTest {

	private SailRepositoryConnection subject;
	private SailConnection sailConnection;
	private SailRepository sailRepository;

	@BeforeEach
	public void setUp() {
		sailConnection = mock(SailConnection.class);
		sailRepository = mock(SailRepository.class);

		subject = new SailRepositoryConnection(sailRepository, sailConnection);
	}

	@Test
	public void testPrepareQuery_not_bypassed() {
		Optional<TupleExpr> response = Optional.empty();
		when(sailConnection.prepareQuery(any(), eq(Query.QueryType.TUPLE), any(), any())).thenReturn(response);
		when(sailConnection.evaluate(any(), any(), any(), anyBoolean())).thenReturn(new EmptyIteration<>());

		TupleQuery query = (TupleQuery) subject.prepareQuery("SELECT * WHERE { ?s ?p ?o }");
		query.evaluate();
		// check that evaluation is still called, and not with an empty TupleExpr
		verify(sailConnection).evaluate(any(TupleExpr.class), any(), any(), anyBoolean());
	}

	@Test
	public void testPrepareQuery_bypassed() {
		TupleExpr expr = mock(TupleExpr.class);
		Optional<TupleExpr> response = Optional.of(expr);
		when(sailConnection.prepareQuery(any(), eq(Query.QueryType.GRAPH), any(), any())).thenReturn(response);
		when(sailConnection.evaluate(eq(expr), any(), any(), anyBoolean())).thenReturn(new EmptyIteration<>());

		GraphQuery query = (GraphQuery) subject.prepareQuery("CONSTRUCT WHERE { ?s ?p ?o }");
		query.evaluate();
		// check that the TupleExpr implementation created by the underlying sail was passed to the evaluation
		verify(sailConnection).evaluate(eq(expr), any(), any(), anyBoolean());
	}

	@Test
	public void testPrepareTupleQuery_not_bypassed() {
		Optional<TupleExpr> response = Optional.empty();
		when(sailConnection.prepareQuery(any(), eq(Query.QueryType.TUPLE), any(), any())).thenReturn(response);
		when(sailConnection.evaluate(any(), any(), any(), anyBoolean())).thenReturn(new EmptyIteration<>());

		TupleQuery query = subject.prepareTupleQuery("SELECT * WHERE { ?s ?p ?o }");
		query.evaluate();
		// check that evaluation is still called, and not with an empty TupleExpr
		verify(sailConnection).evaluate(any(TupleExpr.class), any(), any(), anyBoolean());
	}

	@Test
	public void testPrepareTupleQuery_bypassed() {
		TupleExpr expr = mock(TupleExpr.class);
		Optional<TupleExpr> response = Optional.of(expr);
		when(sailConnection.prepareQuery(any(), eq(Query.QueryType.TUPLE), any(), any())).thenReturn(response);
		when(sailConnection.evaluate(eq(expr), any(), any(), anyBoolean())).thenReturn(new EmptyIteration<>());

		TupleQuery query = subject.prepareTupleQuery("SELECT * WHERE { ?s ?p ?o }");
		query.evaluate();
		// check that the TupleExpr implementation created by the underlying sail was passed to the evaluation
		verify(sailConnection).evaluate(eq(expr), any(), any(), anyBoolean());
	}

	@Test
	public void testPrepareGraphQuery_not_bypassed() {
		Optional<TupleExpr> response = Optional.empty();
		when(sailConnection.prepareQuery(any(), eq(Query.QueryType.GRAPH), any(), any())).thenReturn(response);
		when(sailConnection.evaluate(any(), any(), any(), anyBoolean())).thenReturn(new EmptyIteration<>());

		GraphQuery query = subject.prepareGraphQuery("CONSTRUCT WHERE { ?s ?p ?o }");
		query.evaluate();
		// check that evaluation is still called, and not with an empty TupleExpr
		verify(sailConnection).evaluate(any(TupleExpr.class), any(), any(), anyBoolean());
	}

	@Test
	public void testPrepareGraphQuery_bypassed() {
		TupleExpr expr = mock(TupleExpr.class);
		Optional<TupleExpr> response = Optional.of(expr);
		when(sailConnection.prepareQuery(any(), eq(Query.QueryType.GRAPH), any(), any())).thenReturn(response);
		when(sailConnection.evaluate(eq(expr), any(), any(), anyBoolean())).thenReturn(new EmptyIteration<>());

		GraphQuery query = subject.prepareGraphQuery("CONSTRUCT WHERE { ?s ?p ?o }");
		query.evaluate();
		// check that the TupleExpr implementation created by the underlying sail was passed to the evaluation
		verify(sailConnection).evaluate(eq(expr), any(), any(), anyBoolean());
	}

	@Test
	public void testPrepareBooleanQuery_not_bypassed() {
		Optional<TupleExpr> response = Optional.empty();
		when(sailConnection.prepareQuery(any(), eq(Query.QueryType.BOOLEAN), any(), any())).thenReturn(response);
		when(sailConnection.evaluate(any(), any(), any(), anyBoolean())).thenReturn(new EmptyIteration<>());

		BooleanQuery query = subject.prepareBooleanQuery("ASK WHERE { ?s ?p ?o }");
		query.evaluate();
		// check that evaluation is still called, and not with an empty TupleExpr
		verify(sailConnection).evaluate(any(TupleExpr.class), any(), any(), anyBoolean());
	}

	@Test
	public void testPrepareBooleanQuery_bypassed() {
		TupleExpr expr = mock(TupleExpr.class);
		Optional<TupleExpr> response = Optional.of(expr);
		when(sailConnection.prepareQuery(any(), eq(Query.QueryType.BOOLEAN), any(), any())).thenReturn(response);
		when(sailConnection.evaluate(eq(expr), any(), any(), anyBoolean())).thenReturn(new EmptyIteration<>());

		BooleanQuery query = subject.prepareBooleanQuery("ASK WHERE { ?s ?p ?o }");
		query.evaluate();
		// check that the TupleExpr implementation created by the underlying sail was passed to the evaluation
		verify(sailConnection).evaluate(eq(expr), any(), any(), anyBoolean());
	}

	@Test
	public void testExplainQuery() {
		TupleExpr expr = mock(TupleExpr.class);
		when(expr.clone()).thenReturn(expr);
		Explanation explanation = mock(Explanation.class);

		when(sailConnection.prepareQuery(any(), any(), any(), any())).thenReturn(Optional.of(expr));
		when(sailConnection.explain(any(), any(TupleExpr.class), any(), any(), anyBoolean(), anyInt()))
				.thenReturn(explanation);

		TupleQuery query = subject.prepareTupleQuery("SELECT * WHERE { ?s ?p ?o }");
		assertThat(query.explain(Explanation.Level.Unoptimized)).isEqualTo(explanation);

		verify(sailConnection).explain(eq(Explanation.Level.Unoptimized), any(QueryRoot.class), any(), any(),
				anyBoolean(), anyInt());
	}

}
