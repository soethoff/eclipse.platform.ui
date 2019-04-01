/**
 *  Copyright (c) 2019 Red Hat Inc., and others
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *  - Mickael Istria (Red Hat Inc.)
 */
package org.eclipse.jface.text.tests.codemining;

import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.codemining.ICodeMiningProvider;
import org.eclipse.jface.text.codemining.LineContentCodeMining;

public class StaticContentLineCodeMining extends LineContentCodeMining {

	public StaticContentLineCodeMining(int i, char c, ICodeMiningProvider repeatLettersCodeMiningProvider) {
		super(new Position(i, 1), repeatLettersCodeMiningProvider);
		setLabel(Character.toString(c));
	}

	@Override
	public boolean isResolved() {
		return true;
	}

}
