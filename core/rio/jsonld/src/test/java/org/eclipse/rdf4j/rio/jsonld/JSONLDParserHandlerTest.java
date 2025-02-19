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

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.AbstractParserHandlingTest;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFWriter;

/**
 * Unit tests for {@link JSONLDParser} related to handling of datatypes and languages.
 *
 * @author Peter Ansell
 */
public class JSONLDParserHandlerTest extends AbstractParserHandlingTest {

	@Override
	protected InputStream getRDFLangStringWithNoLanguageStream(Model model) throws Exception {
		InputStream RDFLangStringWithNoLanguageStatements = new FileInputStream(
				"src/test/resources/testcases/jsonld/jsonld-RDF-langString-no-language-test.jsonld");
		return RDFLangStringWithNoLanguageStatements;
	}

	@Override
	protected RDFParser getParser() {
		return new JSONLDParser();
	}

	@Override
	protected RDFWriter createWriter(OutputStream output) {
		return new JSONLDWriter(output);
	}

	@Override
	public final void testUnknownLanguageWithMessageWithFailCase1() throws Exception {
		// Not support by JSON-LD
	}

	@Override
	public void testUnknownLanguageNoMessageNoFailCase1() throws Exception {
		// Not support by JSON-LD
	}

	@Override
	public void testUnknownLanguageNoMessageNoFailCase2() throws Exception {
		// Not support by JSON-LD
	}

	@Override
	public void testUnknownLanguageNoMessageNoFailCase3() throws Exception {
		// Not support by JSON-LD
	}

	@Override
	public void testUnknownLanguageNoMessageNoFailCase4() throws Exception {
		// Not support by JSON-LD
	}

	@Override
	public void testUnknownLanguageNoMessageNoFailCase5() throws Exception {
		// Not support by JSON-LD
	}

	@Override
	public void testUnknownLanguageWithMessageNoFailCase1() throws Exception {
		// Not support by JSON-LD
	}

	@Override
	public void testUnknownLanguageWithMessageNoFailCase2() throws Exception {
		// Not support by JSON-LD
	}

	@Override
	public void testUnknownLanguageWithMessageNoFailCase3() throws Exception {
		// Not support by JSON-LD
	}
}
