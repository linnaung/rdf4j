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
package org.eclipse.rdf4j.rio.jsonld;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.ParserConfig;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.RDFParseException;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.RDFWriterTest;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.RioSetting;
import org.eclipse.rdf4j.rio.WriterConfig;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import no.hasmac.jsonld.JsonLdError;
import no.hasmac.jsonld.document.Document;
import no.hasmac.jsonld.document.JsonDocument;

/**
 * @author Peter Ansell
 */
public class JSONLDWriterTest extends RDFWriterTest {
	private final String exNs = "http://example.org/";

	public JSONLDWriterTest() {
		super(new JSONLDWriterFactory(), new JSONLDParserFactory());
	}

	@Override
	protected void setupWriterConfig(WriterConfig config) {
		super.setupWriterConfig(config);
		config.set(JSONLDSettings.JSONLD_MODE, JSONLDMode.COMPACT);
	}

	@Override
	protected void setupParserConfig(ParserConfig config) {
		super.setupParserConfig(config);
		config.set(BasicParserSettings.FAIL_ON_UNKNOWN_DATATYPES, true);
		config.set(BasicParserSettings.FAIL_ON_UNKNOWN_LANGUAGES, true);
	}

	@Test
	@Override
	@Disabled("TODO: Determine why this test is breaking")
	public void testIllegalPrefix() throws RDFHandlerException, RDFParseException {
	}

	@Test
	public void testEmptyNamespace() {
		IRI uri1 = vf.createIRI(exNs, "uri1");
		IRI uri2 = vf.createIRI(exNs, "uri2");

		StringWriter w = new StringWriter();

		RDFWriter rdfWriter = rdfWriterFactory.getWriter(w);
		rdfWriter.getWriterConfig().set(JSONLDSettings.JSONLD_MODE, JSONLDMode.COMPACT);
		rdfWriter.startRDF();
		rdfWriter.handleNamespace("", exNs);
		rdfWriter.handleNamespace(DCTERMS.PREFIX, DCTERMS.NAMESPACE);
		rdfWriter.handleStatement(vf.createStatement(uri1, DCTERMS.TITLE, vf.createBNode()));
		rdfWriter.handleStatement(vf.createStatement(uri1, uri2, vf.createBNode()));
		rdfWriter.endRDF();

		assertTrue(w.toString().contains("@vocab"), "Does not contain @vocab");
	}

	@Test
	public void testRoundTripNamespaces() throws Exception {
		IRI uri1 = vf.createIRI(exNs, "uri1");
		IRI uri2 = vf.createIRI(exNs, "uri2");
		Literal plainLit = vf.createLiteral("plain", XSD.STRING);

		Statement st1 = vf.createStatement(uri1, uri2, plainLit);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		RDFWriter rdfWriter = rdfWriterFactory.getWriter(out);
		rdfWriter.getWriterConfig().set(JSONLDSettings.JSONLD_MODE, JSONLDMode.COMPACT);
		rdfWriter.startRDF();
		rdfWriter.handleNamespace("ex", exNs);
		rdfWriter.handleStatement(st1);
		rdfWriter.endRDF();

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		RDFParser rdfParser = rdfParserFactory.getParser();
		ParserConfig config = new ParserConfig();
		config.set(BasicParserSettings.FAIL_ON_UNKNOWN_DATATYPES, true);
		config.set(BasicParserSettings.FAIL_ON_UNKNOWN_LANGUAGES, true);
		rdfParser.setParserConfig(config);
		rdfParser.setValueFactory(vf);
		Model model = new LinkedHashModel();
		rdfParser.setRDFHandler(new StatementCollector(model));

		rdfParser.parse(in, "foo:bar");

		assertEquals(1, model.size(), "Unexpected number of statements, found " + model.size());

		assertTrue(model.contains(st1), "missing namespaced statement");

		if (rdfParser.getRDFFormat().supportsNamespaces()) {
			assertTrue(!model.getNamespaces().isEmpty(),
					"Expected at least one namespace, found " + model.getNamespaces().size());
			assertEquals(exNs, model.getNamespace("ex").get().getName());
		}
	}

	/**
	 * Test if the JSON-LD writer honors the "native RDF type" setting.
	 */
	@Test
	public void testNativeRDFTypes() {
		IRI subject = vf.createIRI(exNs, "uri1");
		IRI predicate = vf.createIRI(exNs, "uri2");
		Literal object = vf.createLiteral(true);
		Statement stmt = vf.createStatement(subject, predicate, object);

		StringWriter w = new StringWriter();
		RDFWriter rdfWriter = rdfWriterFactory.getWriter(w);
		rdfWriter.getWriterConfig().set(JSONLDSettings.JSONLD_MODE, JSONLDMode.COMPACT);
		rdfWriter.getWriterConfig().set(JSONLDSettings.COMPACT_ARRAYS, true);
		rdfWriter.getWriterConfig().set(JSONLDSettings.USE_NATIVE_TYPES, true);

		rdfWriter.startRDF();
		rdfWriter.handleStatement(stmt);
		rdfWriter.endRDF();

		assertTrue(!w.toString().contains("@type"), "Does contain @type");
	}

	@Test
	public void testFraming() throws IOException, JsonLdError {
		Model data = Rio.parse(new StringReader("{\n" +
				"  \"@context\": {\n" +
				"    \"dc11\": \"http://purl.org/dc/elements/1.1/\",\n" +
				"    \"ex\": \"http://example.org/vocab#\",\n" +
				"    \"xsd\": \"http://www.w3.org/2001/XMLSchema#\",\n" +
				"    \"ex:contains\": {\n" +
				"      \"@type\": \"@id\"\n" +
				"    }\n" +
				"  },\n" +
				"  \"@graph\": [\n" +
				"    {\n" +
				"      \"@id\": \"http://example.org/library\",\n" +
				"      \"@type\": \"ex:Library\",\n" +
				"      \"ex:contains\": \"http://example.org/library/the-republic\"\n" +
				"    },\n" +
				"    {\n" +
				"      \"@id\": \"http://example.org/library/the-republic\",\n" +
				"      \"@type\": \"ex:Book\",\n" +
				"      \"dc11:creator\": \"Plato\",\n" +
				"      \"dc11:title\": \"The Republic\",\n" +
				"      \"ex:contains\": \"http://example.org/library/the-republic#introduction\"\n" +
				"    },\n" +
				"    {\n" +
				"      \"@id\": \"http://example.org/library/the-republic#introduction\",\n" +
				"      \"@type\": \"ex:Chapter\",\n" +
				"      \"dc11:description\": \"An introductory chapter on The Republic.\",\n" +
				"      \"dc11:title\": \"The Introduction\"\n" +
				"    }\n" +
				"  ]\n" +
				"}"), "", RDFFormat.JSONLD);

		Document frame = JsonDocument.of(new StringReader("{\n" +
				"  \"@context\": {\n" +
				"    \"dc11\": \"http://purl.org/dc/elements/1.1/\",\n" +
				"    \"ex\": \"http://example.org/vocab#\"\n" +
				"  },\n" +
				"  \"@type\": \"ex:Library\",\n" +
				"  \"ex:contains\": {\n" +
				"    \"@type\": \"ex:Book\",\n" +
				"    \"ex:contains\": {\n" +
				"      \"@type\": \"ex:Chapter\"\n" +
				"    }\n" +
				"  }\n" +
				"}"));

		StringWriter stringWriter = new StringWriter();
		RDFWriter rdfWriter = rdfWriterFactory.getWriter(stringWriter);
		rdfWriter.getWriterConfig().set(JSONLDSettings.JSONLD_MODE, JSONLDMode.FRAME);
		rdfWriter.getWriterConfig().set(JSONLDSettings.FRAME, frame);
		rdfWriter.getWriterConfig().set(BasicWriterSettings.PRETTY_PRINT, true);

		rdfWriter.startRDF();
		data.forEach(rdfWriter::handleStatement);
		rdfWriter.endRDF();

		assertEquals(
				"{\n" +
						"    \"@id\": \"http://example.org/library\",\n" +
						"    \"@type\": \"ex:Library\",\n" +
						"    \"ex:contains\": {\n" +
						"        \"@id\": \"http://example.org/library/the-republic\",\n" +
						"        \"@type\": \"ex:Book\",\n" +
						"        \"ex:contains\": {\n" +
						"            \"@id\": \"http://example.org/library/the-republic#introduction\",\n" +
						"            \"@type\": \"ex:Chapter\",\n" +
						"            \"dc11:description\": \"An introductory chapter on The Republic.\",\n" +
						"            \"dc11:title\": \"The Introduction\"\n" +
						"        },\n" +
						"        \"dc11:creator\": \"Plato\",\n" +
						"        \"dc11:title\": \"The Republic\"\n" +
						"    },\n" +
						"    \"@context\": {\n" +
						"        \"dc11\": \"http://purl.org/dc/elements/1.1/\",\n" +
						"        \"ex\": \"http://example.org/vocab#\"\n" +
						"    }\n" +
						"}",
				stringWriter.toString());

	}

	@Override
	protected RioSetting<?>[] getExpectedSupportedSettings() {
		return new RioSetting[] {
				BasicWriterSettings.BASE_DIRECTIVE,
				BasicWriterSettings.PRETTY_PRINT,
				org.eclipse.rdf4j.rio.jsonld.JSONLDSettings.COMPACT_ARRAYS,
				org.eclipse.rdf4j.rio.jsonld.JSONLDSettings.JSONLD_MODE,
				org.eclipse.rdf4j.rio.jsonld.JSONLDSettings.USE_RDF_TYPE,
				org.eclipse.rdf4j.rio.jsonld.JSONLDSettings.USE_NATIVE_TYPES,
				org.eclipse.rdf4j.rio.jsonld.JSONLDSettings.PRODUCE_GENERALIZED_RDF,
				org.eclipse.rdf4j.rio.jsonld.JSONLDSettings.EXCEPTION_ON_WARNING,
				org.eclipse.rdf4j.rio.jsonld.JSONLDSettings.FRAME
		};
	}
}
