/*
 * Copyright (c) 2017..2026 Jérôme Desquilbet <jeromede@fr.ibm.com>
 *
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
*/

package xyz.ccm.client.read;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.workitem.client.IWorkItemClient;
import com.ibm.team.workitem.common.IWorkItemCommon;
import com.ibm.team.workitem.common.model.AttributeTypes;
import com.ibm.team.workitem.common.model.IAttribute;
import com.ibm.team.workitem.common.model.IAttributeHandle;
import com.ibm.team.workitem.common.model.IEnumeration;
import com.ibm.team.workitem.common.model.ILiteral;

import xyz.ccm.client.utils.ProgressMonitor;
import xyz.ccm.model.Attribute;
import xyz.ccm.model.Literal;
import xyz.ccm.model.Project;

public class AttributeHelper {

	public static String readAttributes(IProjectArea pa, IWorkItemClient wiClient, IWorkItemCommon wiCommon,
			IItemManager itemManager, ProgressMonitor monitor, Project p) throws TeamRepositoryException {

		List<String> builtIns = new ArrayList<String>();
		monitor.out("\t\t\tfindAttributes:");
		String result;
		IAttribute attribute;
		for (IAttributeHandle attributeH : wiClient.findBuiltInAttributes(pa, monitor)) {
			attribute = (IAttribute) itemManager.fetchCompleteItem((IItemHandle) attributeH, IItemManager.DEFAULT,
					monitor);
			builtIns.add(attribute.getIdentifier());
			result = readAttribute(attribute, true, wiCommon, monitor, p);
			if (null != result)
				return result;
		}
		for (IAttributeHandle attributeH : wiClient.findAttributes(pa, monitor)) {
			attribute = (IAttribute) itemManager.fetchCompleteItem((IItemHandle) attributeH, IItemManager.DEFAULT,
					monitor);
			if (builtIns.contains(attribute.getIdentifier())) {
				continue;
			}
			result = readAttribute(attribute, false, wiCommon, monitor, p);
			if (null != result)
				return result;
		}
		return null;
	}

	public static String readAttribute(IAttribute attribute, boolean builtIn, IWorkItemCommon wiCommon,
			ProgressMonitor monitor, Project p) throws TeamRepositoryException {

		monitor.out("\t\t\t\t" + attribute.getIdentifier() + " (" + attribute.getDisplayName() + ") ["
				+ attribute.getAttributeType() + "] " + (builtIn ? "builtIn" : "custom"));
		Attribute a;
		Literal lit;
		Collection<Literal> lits = null;
		Literal nullLit = null;
		ILiteral literal;
		ILiteral nullLiteral;
		if (AttributeTypes.isEnumerationAttributeType(attribute.getAttributeType())) {
			lits = new ArrayList<Literal>();
			IEnumeration<? extends ILiteral> enumeration = wiCommon.resolveEnumeration(attribute, monitor);
			nullLiteral = enumeration.findNullEnumerationLiteral();
			monitor.out("\t\t\t\tnull literal: " + ((null == nullLiteral) ? null
					: nullLiteral.getIdentifier2().getStringIdentifier() + " (" + nullLiteral.getName() + ")"));
			if (null != nullLiteral) {
				nullLit = new Literal(nullLiteral.getIdentifier2().getStringIdentifier(), nullLiteral.getName());
				nullLit.setExternalObject(nullLiteral.getName(), nullLiteral);
			}
			for (Object o : enumeration.getEnumerationLiterals()) {
				if (o instanceof ILiteral) {
					literal = (ILiteral) o;
					monitor.out("\t\t\t\tliteral: " + literal.getIdentifier2().getStringIdentifier() + " ("
							+ literal.getName() + ")");
					lit = new Literal(literal.getIdentifier2().getStringIdentifier(), literal.getName());
					lit.setExternalObject(literal.getName(), lit);
					lits.add(lit);
				}
			}
		}
		if (null == lits) {
			a = new Attribute(attribute.getIdentifier(), builtIn, attribute.getDisplayName(),
					attribute.getAttributeType());
		} else {
			a = new Attribute(attribute.getIdentifier(), builtIn, attribute.getDisplayName(),
					attribute.getAttributeType(), lits, nullLit);
		}
		a.setExternalObject(attribute.getIdentifier(), attribute);
		p.putAttribute(a);
		return null;
	}

}
