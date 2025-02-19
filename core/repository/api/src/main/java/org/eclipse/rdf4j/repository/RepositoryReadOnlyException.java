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
package org.eclipse.rdf4j.repository;

/**
 * Indicates that the current write operation did not succeed because the SAIL cannot be written to, it can only be read
 * from.
 *
 * @author James Leigh
 */
public class RepositoryReadOnlyException extends RepositoryException {

	private static final long serialVersionUID = 750575278848692139L;

	public RepositoryReadOnlyException() {
		super();
	}

	public RepositoryReadOnlyException(String msg, Throwable t) {
		super(msg, t);
	}

	public RepositoryReadOnlyException(String msg) {
		super(msg);
	}

	public RepositoryReadOnlyException(Throwable t) {
		super(t);
	}

}
