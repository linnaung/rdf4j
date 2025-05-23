/*******************************************************************************
 * Copyright (c) 2015 Eclipse RDF4J contributors, Aduna, and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
package org.eclipse.rdf4j.query.parser.sparql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.impl.SimpleNamespace;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.MalformedQueryException;
import org.eclipse.rdf4j.query.algebra.AggregateFunctionCall;
import org.eclipse.rdf4j.query.algebra.ArbitraryLengthPath;
import org.eclipse.rdf4j.query.algebra.DeleteData;
import org.eclipse.rdf4j.query.algebra.Extension;
import org.eclipse.rdf4j.query.algebra.Filter;
import org.eclipse.rdf4j.query.algebra.Group;
import org.eclipse.rdf4j.query.algebra.InsertData;
import org.eclipse.rdf4j.query.algebra.Join;
import org.eclipse.rdf4j.query.algebra.Modify;
import org.eclipse.rdf4j.query.algebra.Order;
import org.eclipse.rdf4j.query.algebra.Projection;
import org.eclipse.rdf4j.query.algebra.ProjectionElem;
import org.eclipse.rdf4j.query.algebra.ProjectionElemList;
import org.eclipse.rdf4j.query.algebra.QueryModelNode;
import org.eclipse.rdf4j.query.algebra.QueryRoot;
import org.eclipse.rdf4j.query.algebra.Slice;
import org.eclipse.rdf4j.query.algebra.StatementPattern;
import org.eclipse.rdf4j.query.algebra.Sum;
import org.eclipse.rdf4j.query.algebra.TupleExpr;
import org.eclipse.rdf4j.query.algebra.Union;
import org.eclipse.rdf4j.query.algebra.UpdateExpr;
import org.eclipse.rdf4j.query.algebra.Var;
import org.eclipse.rdf4j.query.parser.ParsedBooleanQuery;
import org.eclipse.rdf4j.query.parser.ParsedGraphQuery;
import org.eclipse.rdf4j.query.parser.ParsedQuery;
import org.eclipse.rdf4j.query.parser.ParsedTupleQuery;
import org.eclipse.rdf4j.query.parser.ParsedUpdate;
import org.eclipse.rdf4j.query.parser.sparql.aggregate.AggregateCollector;
import org.eclipse.rdf4j.query.parser.sparql.aggregate.AggregateFunction;
import org.eclipse.rdf4j.query.parser.sparql.aggregate.AggregateFunctionFactory;
import org.eclipse.rdf4j.query.parser.sparql.aggregate.CustomAggregateFunctionRegistry;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author jeen
 */
public class SPARQLParserTest {

	private SPARQLParser parser;

	@BeforeEach
	public void setUp() {
		parser = new SPARQLParser();
	}

	@AfterEach
	public void tearDown() {
		parser = null;
	}

	/**
	 * Test method for
	 * {@link org.eclipse.rdf4j.query.parser.sparql.SPARQLParser#parseQuery(java.lang.String, java.lang.String)} .
	 */
	@Test
	public void testSourceStringAssignment() {
		String simpleSparqlQuery = "SELECT * WHERE {?X ?P ?Y }";

		ParsedQuery q = parser.parseQuery(simpleSparqlQuery, null);

		assertThat(q.getSourceString()).isEqualTo(simpleSparqlQuery);
	}

	@Test
	public void testInsertDataLineNumberReporting() {
		String insertDataString = "INSERT DATA {\n incorrect reference }";

		try {
			ParsedUpdate u = parser.parseUpdate(insertDataString, null);
			fail("should have resulted in parse exception");
		} catch (MalformedQueryException e) {
			assertThat(e.getMessage()).contains("line 2,");
		}

	}

	@Test
	public void testDeleteDataLineNumberReporting() {
		String deleteDataString = "DELETE DATA {\n incorrect reference }";

		try {
			ParsedUpdate u = parser.parseUpdate(deleteDataString, null);
			fail("should have resulted in parse exception");
		} catch (MalformedQueryException e) {
			assertThat(e.getMessage()).contains("line 2,");
		}
	}

	@Test
	public void testSES1922PathSequenceWithValueConstant() {

		StringBuilder qb = new StringBuilder();
		qb.append("ASK {?A (<foo:bar>)/<foo:foo> <foo:objValue>} ");

		ParsedQuery q = parser.parseQuery(qb.toString(), null);
		TupleExpr te = q.getTupleExpr();

		assertNotNull(te);
		assertTrue(te instanceof QueryRoot);
		te = ((QueryRoot) te).getArg();
		assertTrue(te instanceof Slice);
		Slice s = (Slice) te;
		assertTrue(s.getArg() instanceof Join);
		Join j = (Join) s.getArg();

		assertTrue(j.getLeftArg() instanceof StatementPattern);
		assertTrue(j.getRightArg() instanceof StatementPattern);
		StatementPattern leftArg = (StatementPattern) j.getLeftArg();
		StatementPattern rightArg = (StatementPattern) j.getRightArg();

		assertTrue(leftArg.getObjectVar().equals(rightArg.getSubjectVar()));
		assertEquals(leftArg.getObjectVar().getName(), rightArg.getSubjectVar().getName());
	}

	@Test
	public void testParsedBooleanQueryRootNode() {
		StringBuilder qb = new StringBuilder();
		qb.append("ASK {?a <foo:bar> \"test\"}");

		ParsedBooleanQuery q = (ParsedBooleanQuery) parser.parseQuery(qb.toString(), null);
		TupleExpr te = q.getTupleExpr();
		assertTrue(te instanceof QueryRoot);
		te = ((QueryRoot) te).getArg();
		assertNotNull(te);
		assertTrue(te instanceof Slice);
		assertTrue(te.getParentNode() instanceof QueryRoot);
	}

	/**
	 * Verify that an INSERT with a subselect using a wildcard correctly adds vars to projection
	 *
	 * @see <a href="https://github.com/eclipse/rdf4j/issues/686">#686</a>
	 */
	@Test
	public void testParseWildcardSubselectInUpdate() {
		StringBuilder update = new StringBuilder();
		update.append("INSERT { <urn:a> <urn:b> <urn:c> . } WHERE { SELECT * {?s ?p ?o } }");

		ParsedUpdate parsedUpdate = parser.parseUpdate(update.toString(), null);
		List<UpdateExpr> exprs = parsedUpdate.getUpdateExprs();
		assertEquals(1, exprs.size());

		UpdateExpr expr = exprs.get(0);
		assertTrue(expr instanceof Modify);
		verifySerializable(expr);

		Modify m = (Modify) expr;
		TupleExpr whereClause = m.getWhereExpr();
		assertTrue(whereClause instanceof Projection);
		ProjectionElemList projectionElemList = ((Projection) whereClause).getProjectionElemList();
		assertNotNull(projectionElemList);
		List<ProjectionElem> elements = projectionElemList.getElements();
		assertNotNull(elements);

		assertThat(elements).hasSize(3);
	}

	@Test
	public void testParseIntegerObjectValue() {
		// test that the parser correctly parses the object value as an integer, instead of as a decimal.
		String query = "select ?Concept where { ?Concept a 1. ?Concept2 a 1. } ";
		ParsedTupleQuery q = (ParsedTupleQuery) parser.parseQuery(query, null);
		verifySerializable(q.getTupleExpr());

		// all we're verifying is that the query is parsed without error. If it doesn't parse as integer but as a
		// decimal, the
		// parser will fail, because the statement pattern doesn't end with a full-stop.
		assertNotNull(q);
	}

	@Test
	public void testParsedTupleQueryRootNode() {
		StringBuilder qb = new StringBuilder();
		qb.append("SELECT *  {?a <foo:bar> \"test\"}");

		ParsedTupleQuery q = (ParsedTupleQuery) parser.parseQuery(qb.toString(), null);
		TupleExpr tupleExpr = q.getTupleExpr();
		assertTrue(tupleExpr instanceof QueryRoot);
		verifySerializable(tupleExpr);
		tupleExpr = ((QueryRoot) tupleExpr).getArg();
		assertNotNull(tupleExpr);
		assertTrue(tupleExpr instanceof Projection);
		assertTrue(tupleExpr.getParentNode() instanceof QueryRoot);
	}

	@Test
	public void testParsedGraphQueryRootNode() {
		StringBuilder qb = new StringBuilder();
		qb.append("CONSTRUCT WHERE {?a <foo:bar> \"test\"}");

		ParsedGraphQuery q = (ParsedGraphQuery) parser.parseQuery(qb.toString(), null);

		TupleExpr te = q.getTupleExpr();
		verifySerializable(te);
		assertTrue(te instanceof QueryRoot);
		te = ((QueryRoot) te).getArg();
		assertNotNull(te);
		assertTrue(te instanceof Projection);
		assertTrue(te.getParentNode() instanceof QueryRoot);
	}

	@Test
	public void testOrderByWithAliases1() {
		String queryString = " SELECT ?x (SUM(?v1)/COUNT(?v1) as ?r) WHERE { ?x <urn:foo> ?v1 } GROUP BY ?x ORDER BY ?r";

		ParsedQuery query = parser.parseQuery(queryString, null);

		assertNotNull(query);
		TupleExpr te = query.getTupleExpr();
		verifySerializable(te);
		assertTrue(te instanceof QueryRoot);
		te = ((QueryRoot) te).getArg();
		assertTrue(te instanceof Projection);

		te = ((Projection) te).getArg();

		assertTrue(te instanceof Order);

		te = ((Order) te).getArg();

		assertTrue(te instanceof Extension);
	}

	@Test
	public void testOrderByWithAliases2() {
		String queryString = "SELECT (?l AS ?v)\n"
				+ "WHERE { ?s rdfs:label ?l. }\n"
				+ "ORDER BY ?v";

		ParsedQuery query = parser.parseQuery(queryString, null);

		TupleExpr te = query.getTupleExpr();
		verifySerializable(te);
		assertThat(te).isInstanceOf(QueryRoot.class);
		te = ((QueryRoot) te).getArg();
		assertThat(te).isInstanceOf(Projection.class);

		te = ((Projection) te).getArg();
		assertThat(te).isInstanceOf(Order.class);

		te = ((Order) te).getArg();
		assertThat(te).isInstanceOf(Extension.class);

	}

	@Test
	public void testSES1927UnequalLiteralValueConstants1() {

		StringBuilder qb = new StringBuilder();
		qb.append("ASK {?a <foo:bar> \"test\". ?a <foo:foo> \"test\"@en .} ");

		ParsedQuery q = parser.parseQuery(qb.toString(), null);
		TupleExpr te = q.getTupleExpr();
		verifySerializable(te);
		assertTrue(te instanceof QueryRoot);
		te = ((QueryRoot) te).getArg();
		assertNotNull(te);

		assertTrue(te instanceof Slice);
		Slice s = (Slice) te;
		assertTrue(s.getArg() instanceof Join);
		Join j = (Join) s.getArg();

		assertTrue(j.getLeftArg() instanceof StatementPattern);
		assertTrue(j.getRightArg() instanceof StatementPattern);
		StatementPattern leftArg = (StatementPattern) j.getLeftArg();
		StatementPattern rightArg = (StatementPattern) j.getRightArg();

		assertFalse(leftArg.getObjectVar().equals(rightArg.getObjectVar()));
		assertNotEquals(leftArg.getObjectVar().getName(), rightArg.getObjectVar().getName());
	}

	@Test
	public void testSES1927UnequalLiteralValueConstants2() {

		StringBuilder qb = new StringBuilder();
		qb.append("ASK {?a <foo:bar> \"test\". ?a <foo:foo> \"test\"^^<foo:bar> .} ");

		ParsedQuery q = parser.parseQuery(qb.toString(), null);
		TupleExpr te = q.getTupleExpr();
		verifySerializable(te);
		assertTrue(te instanceof QueryRoot);
		te = ((QueryRoot) te).getArg();
		assertNotNull(te);

		assertTrue(te instanceof Slice);
		Slice s = (Slice) te;
		assertTrue(s.getArg() instanceof Join);
		Join j = (Join) s.getArg();

		assertTrue(j.getLeftArg() instanceof StatementPattern);
		assertTrue(j.getRightArg() instanceof StatementPattern);
		StatementPattern leftArg = (StatementPattern) j.getLeftArg();
		StatementPattern rightArg = (StatementPattern) j.getRightArg();

		assertFalse(leftArg.getObjectVar().equals(rightArg.getObjectVar()));
		assertNotEquals(leftArg.getObjectVar().getName(), rightArg.getObjectVar().getName());
	}

	@Test
	public void testLongUnicode() {
		ParsedUpdate ru = parser.parseUpdate("insert data {<urn:test:foo> <urn:test:bar> \"\\U0001F61F\" .}",
				"urn:test");
		InsertData insertData = (InsertData) ru.getUpdateExprs().get(0);
		String[] lines = insertData.getDataBlock().split("\n");
		assertEquals("\uD83D\uDE1F", lines[lines.length - 1].replaceAll(".*\"(.*)\".*", "$1"));
	}

	@Test
	public void testAdditionalWhitespace_Not_In() {
		String query = "SELECT * WHERE { ?s ?p ?o. FILTER(?o NOT  IN (1, 2, 3)) }";

		ParsedQuery parsedQuery = parser.parseQuery(query, null);

		// parsing should not throw exception
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);

	}

	@Test
	public void testAdditionalWhitespace_Not_Exists() {
		String query = "SELECT * WHERE { ?s ?p ?o. FILTER NOT  EXISTS { ?s ?p ?o } }";

		ParsedQuery parsedQuery = parser.parseQuery(query, null);

		// parsing should not throw exception
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);

	}

	@Test
	public void testWildCardPathFixedEnd() {

		String query = "PREFIX : <http://example.org/>\n ASK {:IBM ((:|!:)|(^:|!^:))* :Jane.} ";

		ParsedQuery parsedQuery = parser.parseQuery(query, null);

		// parsing should not throw exception
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);

	}

	@Test
	public void testWildCardPathPushNegation() {

		String query = "PREFIX : <http://example.org/>\n ASK {:IBM ^(:|!:) ?jane.} ";

		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);
		assertTrue(tupleExpr instanceof QueryRoot);
		tupleExpr = ((QueryRoot) tupleExpr).getArg();

		Slice slice = (Slice) tupleExpr;
		Union union = (Union) slice.getArg();

		Var leftSubjectVar = ((StatementPattern) union.getLeftArg()).getSubjectVar();
		Var rightSubjectVar = ((StatementPattern) ((Filter) union.getRightArg()).getArg()).getSubjectVar();

		assertEquals(leftSubjectVar, rightSubjectVar);

	}

	@Test
	public void testWildCardPathPushNegation2() {

		String query = "PREFIX : <http://example.org/>\n ASK {:IBM ^(:|!:) :Jane.} ";

		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);
		assertTrue(tupleExpr instanceof QueryRoot);
		tupleExpr = ((QueryRoot) tupleExpr).getArg();
		assertTrue(tupleExpr instanceof Slice);
		Slice slice = (Slice) tupleExpr;
		assertTrue(slice.getArg() instanceof Union);
		Union union = (Union) slice.getArg();

		Var leftSubjectVar = ((StatementPattern) union.getLeftArg()).getSubjectVar();
		Var rightSubjectVar = ((StatementPattern) ((Filter) union.getRightArg()).getArg()).getSubjectVar();

		assertEquals(leftSubjectVar, rightSubjectVar);

	}

	@Test
	public void testWildCardPathComplexSubjectHandling() {

		String query = "PREFIX : <http://example.org/>\n ASK { ?a (:comment/^(:subClassOf|(:type/:label))/:type)* ?b } ";

		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);
		assertTrue(tupleExpr instanceof QueryRoot);
		tupleExpr = ((QueryRoot) tupleExpr).getArg();
		assertTrue(tupleExpr instanceof Slice);
		Slice slice = (Slice) tupleExpr;

		ArbitraryLengthPath path = (ArbitraryLengthPath) slice.getArg();
		Var pathStart = path.getSubjectVar();
		Var pathEnd = path.getObjectVar();

		assertThat(pathStart.getName()).isEqualTo("a");
		assertThat(pathEnd.getName()).isEqualTo("b");

		Join pathSequence = (Join) path.getPathExpression();
		Join innerJoin = (Join) pathSequence.getLeftArg();
		Var commentObjectVar = ((StatementPattern) innerJoin.getLeftArg()).getObjectVar();

		Union union = (Union) innerJoin.getRightArg();
		Var subClassOfSubjectVar = ((StatementPattern) union.getLeftArg()).getSubjectVar();
		assertThat(subClassOfSubjectVar).isNotEqualTo(commentObjectVar);

		Var subClassOfObjectVar = ((StatementPattern) union.getLeftArg()).getObjectVar();

		assertThat(subClassOfObjectVar).isEqualTo(commentObjectVar);
	}

	@Test
	public void testGroupByProjectionHandling_NoAggregate() {
		String query = "SELECT DISTINCT ?s (?o AS ?o1) \n"
				+ "WHERE {\n"
				+ "	?s ?p ?o \n"
				+ "} GROUP BY ?s ?o";

		// should parse without error
		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);

	}

	@Test
	public void testProjectionHandling_CustomAggregateFunction() {
		var factory = buildDummyFactory();
		try {
			CustomAggregateFunctionRegistry.getInstance().add(factory);
			String query = "prefix rj: <https://www.rdf4j.org/aggregate#>"
					+ "SELECT (rj:x(distinct ?o) AS ?o1) \n"
					+ "WHERE {\n"
					+ "	?s ?p ?o \n"
					+ "} GROUP BY ?s ?o";
			var tupleExpr = parser.parseQuery(query, null).getTupleExpr();
			verifySerializable(tupleExpr);
			assertTrue(tupleExpr instanceof QueryRoot);
			tupleExpr = ((QueryRoot) tupleExpr).getArg();
			assertTrue(tupleExpr instanceof Projection);
			tupleExpr = ((Projection) tupleExpr).getArg();
			assertTrue(tupleExpr instanceof Extension);
			var extensionElements = ((Extension) tupleExpr).getElements();
			assertEquals(1, extensionElements.size());
			assertTrue(extensionElements.get(0).getExpr() instanceof AggregateFunctionCall);
			assertEquals(factory.getIri(), ((AggregateFunctionCall) extensionElements.get(0).getExpr()).getIRI());
			tupleExpr = ((Extension) tupleExpr).getArg();
			assertTrue(tupleExpr instanceof Group);
			assertEquals(1, ((Group) tupleExpr).getGroupElements().size());
			assertEquals(extensionElements.get(0).getExpr(),
					((Group) tupleExpr).getGroupElements().get(0).getOperator());
			assertTrue(((Group) tupleExpr).getGroupElements().get(0).getOperator().isDistinct());
		} finally {
			CustomAggregateFunctionRegistry.getInstance().remove(factory);
		}
	}

	@Test
	public void testProjectionHandling_HavingCustomAggregateFunction() {
		var factory = buildDummyFactory();
		try {
			CustomAggregateFunctionRegistry.getInstance().add(factory);
			String query = "prefix rj: <https://www.rdf4j.org/aggregate#>"
					+ "SELECT (rj:x(distinct ?o) AS ?o1) \n"
					+ "WHERE {\n"
					+ "	?s ?p ?o \n"
					+ "} GROUP BY ?s \nHAVING (rj:x(distinct ?o) > 50) ";
			var tupleExpr = parser.parseQuery(query, null).getTupleExpr();
			verifySerializable(tupleExpr);
			assertTrue(tupleExpr instanceof QueryRoot);
			tupleExpr = ((QueryRoot) tupleExpr).getArg();
			assertTrue(tupleExpr instanceof Projection);
			tupleExpr = ((Projection) tupleExpr).getArg();
			assertTrue(tupleExpr instanceof Extension);
			var extensionElements = ((Extension) tupleExpr).getElements();
			assertEquals(1, extensionElements.size());
			assertTrue(extensionElements.get(0).getExpr() instanceof AggregateFunctionCall);
			assertEquals(factory.getIri(), ((AggregateFunctionCall) extensionElements.get(0).getExpr()).getIRI());
			tupleExpr = ((Extension) tupleExpr).getArg();
			assertTrue(tupleExpr instanceof Filter);
			tupleExpr = ((Filter) tupleExpr).getArg();
			assertTrue(tupleExpr instanceof Extension);
			tupleExpr = ((Extension) tupleExpr).getArg();
			assertTrue(tupleExpr instanceof Group);
			assertEquals(2, ((Group) tupleExpr).getGroupElements().size());
			assertEquals(extensionElements.get(0).getExpr(),
					((Group) tupleExpr).getGroupElements().get(0).getOperator());
			assertEquals(extensionElements.get(0).getExpr(),
					((Group) tupleExpr).getGroupElements().get(1).getOperator());
			assertTrue(((Group) tupleExpr).getGroupElements().get(0).getOperator().isDistinct());
		} finally {
			CustomAggregateFunctionRegistry.getInstance().remove(factory);
		}
	}

	@Test
	public void testProjectionHandling_MultipleAggregateFunction() {
		var factory = buildDummyFactory();
		try {
			CustomAggregateFunctionRegistry.getInstance().add(factory);
			String query = "prefix rj: <https://www.rdf4j.org/aggregate#>"
					+ "SELECT (rj:x(distinct ?o) AS ?o1) (sum(?o) AS ?o2)\n"
					+ "WHERE {\n"
					+ "	?s ?p ?o \n"
					+ "} GROUP BY ?s ?o";
			var tupleExpr = parser.parseQuery(query, null).getTupleExpr();
			verifySerializable(tupleExpr);
			assertTrue(tupleExpr instanceof QueryRoot);
			tupleExpr = ((QueryRoot) tupleExpr).getArg();
			assertTrue(tupleExpr instanceof Projection);
			tupleExpr = ((Projection) tupleExpr).getArg();
			assertTrue(tupleExpr instanceof Extension);
			var extensionElements = ((Extension) tupleExpr).getElements();
			assertEquals(2, extensionElements.size());
			assertTrue(extensionElements.get(0).getExpr() instanceof AggregateFunctionCall);
			assertTrue(extensionElements.get(1).getExpr() instanceof Sum);
			assertEquals(factory.getIri(), ((AggregateFunctionCall) extensionElements.get(0).getExpr()).getIRI());
			tupleExpr = ((Extension) tupleExpr).getArg();
			assertTrue(tupleExpr instanceof Group);
			assertEquals(2, ((Group) tupleExpr).getGroupElements().size());
			assertEquals(extensionElements.get(0).getExpr(),
					((Group) tupleExpr).getGroupElements().get(0).getOperator());
			assertTrue(((Group) tupleExpr).getGroupElements().get(0).getOperator().isDistinct());
		} finally {
			CustomAggregateFunctionRegistry.getInstance().remove(factory);
		}
	}

	@Test
	public void testProjectionHandling_UnknownFunctionCallWithDistinct() {
		String query = "prefix rj: <https://www.rdf4j.org/aggregate#>"
				+ "SELECT (rj:x(distinct ?o) AS ?o1) \n"
				+ "WHERE {\n"
				+ "	?s ?p ?o \n"
				+ "} GROUP BY ?s ?o";
		assertDoesNotThrow(() -> parser.parseQuery(query, null));
	}

	@Test
	public void testProjectionHandling_FunctionCallWithArgsFails() {
		var factory = buildDummyFactory();
		String query = "prefix rj: <https://www.rdf4j.org/aggregate#>"
				+ "SELECT (rj:x(?o, ?p) AS ?o1) \n"
				+ "WHERE {\n"
				+ "	?s ?p ?o \n"
				+ "} GROUP BY ?s ?o";
		try {
			CustomAggregateFunctionRegistry.getInstance().add(factory);
			parser.parseQuery(query, null);
			fail("Should not be able to parse function calls with multiple args");
		} catch (Exception e) {
			assertTrue(e instanceof IllegalArgumentException);
		} finally {
			CustomAggregateFunctionRegistry.getInstance().remove(factory);
		}
	}

	@Test
	public void testGroupByProjectionHandling_Aggregate_NonSimpleExpr() {
		String query = "SELECT (COUNT(?s) as ?count) (?o + ?s AS ?o1) \n"
				+ "WHERE {\n"
				+ "	?s ?p ?o \n"
				+ "} GROUP BY ?o";

		assertThatExceptionOfType(MalformedQueryException.class).isThrownBy(() -> parser.parseQuery(query, null))
				.withMessageStartingWith("non-aggregate expression 'MathExpr (+)");

	}

	@Test
	public void testGroupByProjectionHandling_Aggregate_Alias() {
		String query = "SELECT (COUNT(?s) as ?count) (?o AS ?o1) \n"
				+ "WHERE {\n"
				+ "	?s ?p ?o \n"
				+ "} GROUP BY ?o";

		// should parse without error
		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);
	}

	@Test
	public void testGroupByProjectionHandling_Aggregate_Alias2() {
		String query = "SELECT (COUNT(?s) as ?count) (?o AS ?o1) \n"
				+ "WHERE {\n"
				+ "	?s ?p ?o \n"
				+ "} GROUP BY ?p";

		assertThatExceptionOfType(MalformedQueryException.class).isThrownBy(() -> parser.parseQuery(query, null))
				.withMessageStartingWith("non-aggregate expression 'Var (name=o)");
	}

	@Test
	public void testGroupByProjectionHandling_Aggregate_SimpleExpr() {
		String query = "SELECT (COUNT(?s) as ?count) ?o \n"
				+ "WHERE {\n"
				+ "	?s ?p ?o \n"
				+ "} GROUP BY ?p";

		assertThatExceptionOfType(MalformedQueryException.class).isThrownBy(() -> parser.parseQuery(query, null))
				.withMessageStartingWith("variable 'o' in projection not present in GROUP BY.");

	}

	@Test
	public void testGroupByProjectionHandling_Aggregate_SimpleExpr2() {
		String query = "SELECT (COUNT(?s) as ?count) ?o \n"
				+ "WHERE {\n"
				+ "	?s ?p ?o \n"
				+ "} GROUP BY ?o";

		// should parse without error
		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);
	}

	@Test
	public void testGroupByProjectionHandling_Aggregate_Constant() {
		String query = "SELECT (COUNT(?s) as ?count) (<foo:constant> as ?constant) \n"
				+ "WHERE {\n"
				+ "	?s ?p ?o \n"
				+ "} GROUP BY ?o";

		// should parse without error
		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);
	}

	@Test
	public void testGroupByProjectionHandling_variableEffectivelyAggregationResult() {
		String query = "SELECT (COUNT (*) AS ?count) (?count / ?count AS ?result) (?result AS ?temp) (?temp / 2 AS ?temp2) {\n"
				+
				"    ?s a ?o .\n" +
				"}";

		// should parse without error
		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);
	}

	@Test
	public void testGroupByProjectionHandling_effectivelyConstant() {
		String query = "SELECT  (2  AS ?constant1) (?constant1  AS ?constant2) (?constant2/2  AS ?constant3){\n" +
				"    ?o ?p ?o .\n" +
				"} GROUP BY ?o";

		// should parse without error
		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);
	}

	@Test
	public void testGroupByProjectionHandling_renameVariable() {
		String query = "SELECT ?o (?o  AS ?o2) (?o2  AS ?o3) (?o3/2  AS ?o4){\n" +
				"    ?o ?p ?o .\n" +
				"} GROUP BY ?o";

		// should parse without error
		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);
	}

	@Test
	public void testGroupByProjectionHandling_renameVariableWithAggregation() {
		String query = "SELECT ?o (?o  AS ?o2) (COUNT (*) AS ?count) (?o2/?count  AS ?newCount){\n" +
				"    ?o ?p ?o .\n" +
				"} GROUP BY ?o";

		// should parse without error
		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);
	}

	@Test
	public void testDefaultPrefixes() {
		String query = "SELECT ?s {?s ex:aaa ex:ooo}";

		Set<Namespace> defaultPrefixes = new HashSet<>();
		defaultPrefixes.add(new SimpleNamespace("ex", "http://example.org/"));
		SPARQLParser parser = new SPARQLParser(defaultPrefixes);

		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);
	}

	@Test
	public void testDefaultPrefixesOverride() {
		final String namespaceEx1 = "http://example1.org/";
		final String namespaceEx2 = "http://example2.org/";

		assertNotEquals(namespaceEx1, namespaceEx2);

		String query = "PREFIX ex: <" + namespaceEx1 + "> CONSTRUCT {?s ex:bbb ex:ccc} WHERE {?s ex:aaa ex:ooo}";

		Set<Namespace> defaultPrefixes = new HashSet<>();
		defaultPrefixes.add(new SimpleNamespace("ex", namespaceEx2));
		SPARQLParser parser = new SPARQLParser(defaultPrefixes);

		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		assertInstanceOf(ParsedGraphQuery.class, parsedQuery);
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);

		ParsedGraphQuery parsedGraphQuery = (ParsedGraphQuery) parsedQuery;

		assertEquals(namespaceEx1, parsedGraphQuery.getQueryNamespaces().get("ex"));
	}

	@Test
	public void testDefaultPrefixesNoOverride() {
		final String namespaceEx1 = "http://example1.org/";

		String query = "CONSTRUCT {?s ex:bbb ex:ccc} WHERE {?s ex:aaa ex:ooo}";

		Set<Namespace> defaultPrefixes = new HashSet<>();
		defaultPrefixes.add(new SimpleNamespace("ex", namespaceEx1));
		SPARQLParser parser = new SPARQLParser(defaultPrefixes);

		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		assertInstanceOf(ParsedGraphQuery.class, parsedQuery);
		TupleExpr tupleExpr = parsedQuery.getTupleExpr();
		verifySerializable(tupleExpr);

		ParsedGraphQuery parsedGraphQuery = (ParsedGraphQuery) parsedQuery;

		assertEquals(namespaceEx1, parsedGraphQuery.getQueryNamespaces().get("ex"));
	}

	@Test
	public void testNoDefaultPrefixes() {
		assertThrows(MalformedQueryException.class, () -> {
			String query = "SELECT ?s {?s ex:aaa ex:ooo}";

			HashSet<Namespace> customPrefixes = new HashSet<>();
			SPARQLParser parser = new SPARQLParser(customPrefixes);

			parser.parseQuery(query, null);
		});
	}

	@Test
	public void testDefaultPrefixesInsertDataOverride() throws IOException {
		final String namespaceEx1 = "http://example1.org/";
		final String namespaceEx2 = "http://example2.org/";
		String query = "PREFIX ex: <" + namespaceEx2 + "> INSERT DATA { ex:A ex:P ex:B . }";

		Set<Namespace> defaultPrefixes = new HashSet<>();
		defaultPrefixes.add(new SimpleNamespace("ex", namespaceEx1));
		SPARQLParser parser = new SPARQLParser(defaultPrefixes);

		ParsedUpdate parsedUpdate = parser.parseUpdate(query, null);
		assertEquals(1, parsedUpdate.getUpdateExprs().size());
		UpdateExpr expr = parsedUpdate.getUpdateExprs().get(0);
		verifySerializable(expr);

		assertInstanceOf(InsertData.class, expr);
		Model parse = Rio.parse(new StringReader(((InsertData) expr).getDataBlock()), RDFFormat.TURTLE);
		Iterable<Statement> iterable = parse.getStatements(null, null, null);
		Iterator<Statement> it = iterable.iterator();
		assertTrue(it.hasNext());
		Statement line = it.next();
		assertFalse(it.hasNext());
		assertEquals(namespaceEx2 + "A", line.getSubject().stringValue());
		assertEquals(namespaceEx2 + "P", line.getPredicate().stringValue());
		assertEquals(namespaceEx2 + "B", line.getObject().stringValue());
	}

	@Test
	public void testDefaultPrefixesInsertDataInsideOverride() throws IOException {
		final String namespaceEx1 = "http://example1.org/";
		final String namespaceEx2 = "http://example2.org/";
		String query = "INSERT DATA { PREFIX ex: <" + namespaceEx2 + "> ex:A ex:P ex:B . }";

		Set<Namespace> defaultPrefixes = new HashSet<>();
		defaultPrefixes.add(Values.namespace("ex", namespaceEx1));
		SPARQLParser parser = new SPARQLParser(defaultPrefixes);

		ParsedUpdate parsedUpdate = parser.parseUpdate(query, null);
		assertEquals(1, parsedUpdate.getUpdateExprs().size());
		UpdateExpr expr = parsedUpdate.getUpdateExprs().get(0);
		verifySerializable(expr);

		assertInstanceOf(InsertData.class, expr);
		Model parse = Rio.parse(new StringReader(((InsertData) expr).getDataBlock()), RDFFormat.TURTLE);
		Iterable<Statement> iterable = parse.getStatements(null, null, null);
		Iterator<Statement> it = iterable.iterator();
		assertTrue(it.hasNext());
		Statement line = it.next();
		assertFalse(it.hasNext());
		assertEquals(namespaceEx2 + "A", line.getSubject().stringValue());
		assertEquals(namespaceEx2 + "P", line.getPredicate().stringValue());
		assertEquals(namespaceEx2 + "B", line.getObject().stringValue());
	}

	@Test
	public void testDefaultPrefixesInsertDataNoOverride() throws IOException {
		final String namespaceEx1 = "http://example1.org/";
		String query = "INSERT DATA { ex:A ex:P ex:B . }";

		Set<Namespace> defaultPrefixes = new HashSet<>();
		defaultPrefixes.add(Values.namespace("ex", namespaceEx1));
		SPARQLParser parser = new SPARQLParser(defaultPrefixes);

		ParsedUpdate parsedUpdate = parser.parseUpdate(query, null);
		assertEquals(1, parsedUpdate.getUpdateExprs().size());
		UpdateExpr expr = parsedUpdate.getUpdateExprs().get(0);
		verifySerializable(expr);

		assertInstanceOf(InsertData.class, expr);
		Model parse = Rio.parse(new StringReader(((InsertData) expr).getDataBlock()), RDFFormat.TURTLE);
		Iterable<Statement> iterable = parse.getStatements(null, null, null);
		Iterator<Statement> it = iterable.iterator();
		assertTrue(it.hasNext());
		Statement line = it.next();
		assertFalse(it.hasNext());
		assertEquals(namespaceEx1 + "A", line.getSubject().stringValue());
		assertEquals(namespaceEx1 + "P", line.getPredicate().stringValue());
		assertEquals(namespaceEx1 + "B", line.getObject().stringValue());
	}

	@Test
	public void testDefaultPrefixesDeleteDataOverride() throws IOException {
		final String namespaceEx1 = "http://example1.org/";
		final String namespaceEx2 = "http://example2.org/";
		String query = "PREFIX ex: <" + namespaceEx2 + "> DELETE DATA { ex:A ex:P ex:B . }";

		Set<Namespace> defaultPrefixes = new HashSet<>();
		defaultPrefixes.add(Values.namespace("ex", namespaceEx1));
		SPARQLParser parser = new SPARQLParser(defaultPrefixes);

		ParsedUpdate parsedUpdate = parser.parseUpdate(query, null);
		assertEquals(1, parsedUpdate.getUpdateExprs().size());
		UpdateExpr expr = parsedUpdate.getUpdateExprs().get(0);
		verifySerializable(expr);

		assertInstanceOf(DeleteData.class, expr);
		Model parse = Rio.parse(new StringReader(((DeleteData) expr).getDataBlock()), RDFFormat.TURTLE);
		Iterable<Statement> iterable = parse.getStatements(null, null, null);
		Iterator<Statement> it = iterable.iterator();
		assertTrue(it.hasNext());
		Statement line = it.next();
		assertFalse(it.hasNext());
		assertEquals(namespaceEx2 + "A", line.getSubject().stringValue());
		assertEquals(namespaceEx2 + "P", line.getPredicate().stringValue());
		assertEquals(namespaceEx2 + "B", line.getObject().stringValue());
	}

	@Test
	public void testDefaultPrefixesDeleteDataInsideOverride() throws IOException {
		final String namespaceEx1 = "http://example1.org/";
		final String namespaceEx2 = "http://example2.org/";
		String query = "DELETE DATA { PREFIX ex: <" + namespaceEx2 + "> ex:A ex:P ex:B . }";

		Set<Namespace> defaultPrefixes = new HashSet<>();
		defaultPrefixes.add(Values.namespace("ex", namespaceEx1));
		SPARQLParser parser = new SPARQLParser(defaultPrefixes);

		ParsedUpdate parsedUpdate = parser.parseUpdate(query, null);
		assertEquals(1, parsedUpdate.getUpdateExprs().size());
		UpdateExpr expr = parsedUpdate.getUpdateExprs().get(0);
		verifySerializable(expr);

		assertInstanceOf(DeleteData.class, expr);
		Model parse = Rio.parse(new StringReader(((DeleteData) expr).getDataBlock()), RDFFormat.TURTLE);
		Iterable<Statement> iterable = parse.getStatements(null, null, null);
		Iterator<Statement> it = iterable.iterator();
		assertTrue(it.hasNext());
		Statement line = it.next();
		assertFalse(it.hasNext());
		assertEquals(namespaceEx2 + "A", line.getSubject().stringValue());
		assertEquals(namespaceEx2 + "P", line.getPredicate().stringValue());
		assertEquals(namespaceEx2 + "B", line.getObject().stringValue());
	}

	@Test
	public void testDefaultPrefixesDeleteDataNoOverride() throws IOException {
		final String namespaceEx1 = "http://example1.org/";
		String query = "DELETE DATA { ex:A ex:P ex:B . }";

		Set<Namespace> defaultPrefixes = new HashSet<>();
		defaultPrefixes.add(Values.namespace("ex", namespaceEx1));
		SPARQLParser parser = new SPARQLParser(defaultPrefixes);

		ParsedUpdate parsedUpdate = parser.parseUpdate(query, null);
		assertEquals(1, parsedUpdate.getUpdateExprs().size());
		UpdateExpr expr = parsedUpdate.getUpdateExprs().get(0);
		verifySerializable(expr);

		assertInstanceOf(DeleteData.class, expr);
		Model parse = Rio.parse(new StringReader(((DeleteData) expr).getDataBlock()), RDFFormat.TURTLE);
		Iterable<Statement> iterable = parse.getStatements(null, null, null);
		Iterator<Statement> it = iterable.iterator();
		assertTrue(it.hasNext());
		Statement line = it.next();
		assertFalse(it.hasNext());
		assertEquals(namespaceEx1 + "A", line.getSubject().stringValue());
		assertEquals(namespaceEx1 + "P", line.getPredicate().stringValue());
		assertEquals(namespaceEx1 + "B", line.getObject().stringValue());
	}

	@Test
	public void testGroupByProjectionHandling_function() {
		String query = "select (strlen(concat(strlen(?s)+2, \"abc\", count(?o))) as ?len) where { \n" +
				"?s ?p ?o .\n" +
				"} group by ?s";

		// should parse without error
		ParsedQuery parsedQuery = parser.parseQuery(query, null);
		verifySerializable(parsedQuery.getTupleExpr());
	}

	@Test
	public void testApostrophe() {
		String query = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
				"select * where { \n" +
				"  rdf:Test\\'s ?p1 ?o1\n" +
				"}";

		HashSet<Namespace> customPrefixes = new HashSet<>();
		SPARQLParser parser = new SPARQLParser(customPrefixes);

		parser.parseQuery(query, null);
	}

	@Test
	public void testApostropheInsertData() {
		String query = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
				"INSERT DATA { \n" +
				"  rdf:Test\\'s a <http://example.com/Test> .\n" +
				"}";

		HashSet<Namespace> customPrefixes = new HashSet<>();
		SPARQLParser parser = new SPARQLParser(customPrefixes);

		parser.parseUpdate(query, null);
	}

	private AggregateFunctionFactory buildDummyFactory() {
		return new AggregateFunctionFactory() {
			@Override
			public String getIri() {
				return "https://www.rdf4j.org/aggregate#x";
			}

			@Override
			public AggregateFunction buildFunction(Function<BindingSet, Value> evaluationStep) {
				return null;
			}

			@Override
			public AggregateCollector getCollector() {
				return null;
			}
		};
	}

	private void verifySerializable(QueryModelNode tupleExpr) {
		byte[] bytes = objectToBytes(tupleExpr);
		QueryModelNode parsed = (QueryModelNode) bytesToObject(bytes);
		assertEquals(tupleExpr, parsed);
	}

	private byte[] objectToBytes(Serializable object) {
		try (var byteArrayOutputStream = new ByteArrayOutputStream()) {
			try (var objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
				objectOutputStream.writeObject(object);
			}
			return byteArrayOutputStream.toByteArray();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Object bytesToObject(byte[] str) {
		try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(str)) {
			try (ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
				return objectInputStream.readObject();
			}
		} catch (IOException | ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
}
