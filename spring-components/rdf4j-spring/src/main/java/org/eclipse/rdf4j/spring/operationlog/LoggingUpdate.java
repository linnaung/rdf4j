/*******************************************************************************
 * Copyright (c) 2021 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/

package org.eclipse.rdf4j.spring.operationlog;

import java.lang.invoke.MethodHandles;

import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.query.UpdateExecutionException;
import org.eclipse.rdf4j.spring.operationlog.log.OperationLog;
import org.eclipse.rdf4j.spring.support.query.DelegatingUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Florian Kleedorfer
 * @since 4.0.0
 */
public class LoggingUpdate extends DelegatingUpdate {

	private final OperationLog operationLog;

	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public LoggingUpdate(Update delegate, OperationLog operationLog) {
		super(delegate);
		this.operationLog = operationLog;
	}

	@Override
	public void execute() throws UpdateExecutionException {
		operationLog.runWithLog(getDelegate(), () -> getDelegate().execute());
	}
}
