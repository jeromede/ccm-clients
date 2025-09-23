package xyz.ccm.client.read;

import java.util.ArrayList;
import java.util.List;

import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
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
import xyz.ccm.model.Project;

public class AttributeHelper {
	
	public static String readAttributes(ITeamRepository repo, IProjectArea pa, boolean complete,
			IWorkItemClient wiClient, IWorkItemCommon wiCommon, IItemManager itemManager, ProgressMonitor monitor,
			Project p, String dir) {

		monitor.out("\t\t\tfindAttributes:");
		List<IAttributeHandle> allAttributes = new ArrayList<IAttributeHandle>();
		try {
			allAttributes.addAll(wiClient.findBuiltInAttributes(pa, monitor));
			allAttributes.addAll(wiClient.findAttributes(pa, monitor));
		} catch (TeamRepositoryException e2) {
			e2.printStackTrace();
			return "\t\t\t\texception in findAttributes";
		}
		IAttribute attribute;
		try {
			for (IAttributeHandle attributeH : allAttributes) {
				attribute = (IAttribute) repo.itemManager().fetchCompleteItem((IItemHandle) attributeH,
						IItemManager.DEFAULT, monitor);
				if (attribute.isInternal())
					continue;
				monitor.out("\t\t\t\tgetDisplayName:getAttributeType: " + attribute.getDisplayName() + ":"
						+ attribute.getAttributeType() + " : " + attribute.toString());
				if (AttributeTypes.isEnumerationAttributeType(attribute.getAttributeType())) {
					IEnumeration<? extends ILiteral> enumeration = wiCommon.resolveEnumeration(attributeH, monitor);
					List<? extends ILiteral> ll = enumeration.getEnumerationLiterals();
					for (ILiteral l : ll) {
						monitor.out("\t\t\t\t\tliteral id=name : " + l.getIdentifier2().getStringIdentifier() + "="
								+ l.getName());
					}
				}
			}
		} catch (Exception e1) {
			e1.printStackTrace();
			return "ERROR";
		}
		try {
			wiClient.findBuiltInAttributes(pa, monitor);
		} catch (TeamRepositoryException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return null;
	}


}
