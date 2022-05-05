/*******************************************************************************
 * Copyright (c) 2021 Eclipse RDF4J contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *******************************************************************************/

package org.eclipse.rdf4j.spring.dao.exception;

/**
 * @since 4.0.0
 * @author Florian Kleedorfer
 */
public class UnexpectedResultException extends RDF4JDaoException {
	public UnexpectedResultException() {
	}

	public UnexpectedResultException(String message) {
		super(message);
	}

	public UnexpectedResultException(String message, Throwable cause) {
		super(message, cause);
	}

	public UnexpectedResultException(Throwable cause) {
		super(cause);
	}

}