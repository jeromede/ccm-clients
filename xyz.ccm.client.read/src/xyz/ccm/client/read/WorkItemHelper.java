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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.ibm.team.foundation.common.text.XMLString;
import com.ibm.team.links.common.IItemReference;
import com.ibm.team.links.common.ILink;
import com.ibm.team.links.common.IReference;
import com.ibm.team.links.common.IURIReference;
import com.ibm.team.links.common.registry.IEndPointDescriptor;
import com.ibm.team.process.common.IIterationHandle;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IContributorHandle;
import com.ibm.team.repository.common.IFetchResult;
import com.ibm.team.repository.common.IItemHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.workitem.client.IAuditableClient;
import com.ibm.team.workitem.client.IQueryClient;
import com.ibm.team.workitem.client.IWorkItemClient;
import com.ibm.team.workitem.common.IWorkItemCommon;
import com.ibm.team.workitem.common.expression.AttributeExpression;
import com.ibm.team.workitem.common.expression.Expression;
import com.ibm.team.workitem.common.expression.IQueryableAttribute;
import com.ibm.team.workitem.common.expression.QueryableAttributes;
import com.ibm.team.workitem.common.model.AttributeOperation;
import com.ibm.team.workitem.common.model.IApproval;
import com.ibm.team.workitem.common.model.IApprovalDescriptor;
import com.ibm.team.workitem.common.model.IApprovals;
import com.ibm.team.workitem.common.model.IAttachment;
import com.ibm.team.workitem.common.model.IAttachmentHandle;
import com.ibm.team.workitem.common.model.IAttribute;
import com.ibm.team.workitem.common.model.IAttributeHandle;
import com.ibm.team.workitem.common.model.ICategory;
import com.ibm.team.workitem.common.model.IComment;
import com.ibm.team.workitem.common.model.ILiteral;
import com.ibm.team.workitem.common.model.IPriority;
import com.ibm.team.workitem.common.model.IResolution;
import com.ibm.team.workitem.common.model.ISeverity;
import com.ibm.team.workitem.common.model.IState;
import com.ibm.team.workitem.common.model.ISubscriptions;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.IWorkItemHandle;
import com.ibm.team.workitem.common.model.IWorkItemReferences;
import com.ibm.team.workitem.common.model.Identifier;
import com.ibm.team.workitem.common.query.IQueryResult;
import com.ibm.team.workitem.common.query.IResolvedResult;
import com.ibm.team.workitem.common.workflow.IWorkflowInfo;

import xyz.ccm.client.utils.ProgressMonitor;
import xyz.ccm.model.Approval;
import xyz.ccm.model.Artifact;
import xyz.ccm.model.Attachment;
import xyz.ccm.model.Attribute;
import xyz.ccm.model.Comment;
import xyz.ccm.model.Link;
import xyz.ccm.model.Literal;
import xyz.ccm.model.Project;
import xyz.ccm.model.Task;
import xyz.ccm.model.TaskType;
import xyz.ccm.model.TaskVersion;
import xyz.ccm.model.Value;

public class WorkItemHelper {

	static String readWorkItems(ITeamRepository repo, IProjectArea pa, boolean complete, IWorkItemClient wiClient,
			IWorkItemCommon wiCommon, IItemManager itemManager, ProgressMonitor monitor, Project p, String dir) {

		String result;
		monitor.out("Now reading work items...");
		IAuditableClient auditableClient = (IAuditableClient) repo.getClientLibrary(IAuditableClient.class);
		IQueryClient queryClient = (IQueryClient) repo.getClientLibrary(IQueryClient.class);
		IQueryableAttribute attribute = null;
		try {
			attribute = QueryableAttributes.getFactory(IWorkItem.ITEM_TYPE).findAttribute(pa,
					IWorkItem.PROJECT_AREA_PROPERTY, auditableClient, monitor);
		} catch (TeamRepositoryException e) {
			e.printStackTrace();
			return "error finding work item attributes";
		}
		Expression expression = new AttributeExpression(attribute, AttributeOperation.EQUALS, pa);
		IQueryResult<IResolvedResult<IWorkItem>> results = queryClient.getResolvedExpressionResults(pa, expression,
				IWorkItem.FULL_PROFILE);
		results.setLimit(Integer.MAX_VALUE);
		try {
			while (results.hasNext(monitor)) {
				result = readWorkItem(results.next(monitor).getItem(), repo, pa, complete, wiClient, wiCommon,
						itemManager, monitor, p, dir);
				if (null != result)
					return result;
			}
		} catch (TeamRepositoryException e) {
			e.printStackTrace();
			return "error while reading set of work items";
		}
		monitor.out("... work items read.");
		return null;
	}

	static String readWorkItem(IWorkItem wi, ITeamRepository repo, IProjectArea pa, boolean complete,
			IWorkItemClient wiClient, IWorkItemCommon wiCommon, IItemManager itemManager, ProgressMonitor monitor,
			Project p, String dir) throws TeamRepositoryException {

		String result;
		monitor.out("\tNow reading work item " + wi.getId());
		Task task;
		//
		// Task
		//
		task = new Task(//
				wi.getItemId().getUuidValue(), //
				wi.getId(), //
				p.getMember(wi.getCreator().getItemId().getUuidValue()), //
				wi.getCreationDate());
		p.putTask(task);
		monitor.out("\t... just added work item " + task.getSourceId() + " (" + task.getId() + ')');
		result = readWorkItemVersions(wi, repo, pa, complete, wiClient, wiCommon, itemManager, monitor, p, task, dir);
		if (null != result)
			return result;
		//
		// Links (includes attachments and artifacts (aka URIs)
		//
		result = readLinks(wi, repo, pa, wiClient, wiCommon, itemManager, monitor, p, task, dir);
		if (null != result)
			return result;
		//
		// Approvals
		//
		IApprovals approvals = wi.getApprovals();
		IApprovalDescriptor descriptor;
		for (IApproval approval : approvals.getContents()) {
			descriptor = approval.getDescriptor();
			task.addApproval(new Approval(//
					descriptor.getName(), //
					descriptor.getTypeIdentifier(), //
					approval.getStateIdentifier(), //
					descriptor.getDueDate(), //
					p.getMember(approval.getApprover().getItemId().getUuidValue())//
			));
		}
		monitor.out("\t... work item " + wi.getId() + " read.");
		return null;
	}

	private static String readWorkItemVersions(IWorkItem wi, ITeamRepository repo, IProjectArea pa, boolean complete,
			IWorkItemClient wiClient, IWorkItemCommon wiCommon, IItemManager itemManager, ProgressMonitor monitor,
			Project p, Task task, String dir) throws TeamRepositoryException {

		String result = null;
		List<IWorkItem> workItems;
		try {
			workItems = getHistory(wi, itemManager, monitor);
		} catch (TeamRepositoryException e) {
			e.printStackTrace();
			return "error reading history of work item " + wi.getId();
		}
		if (workItems.isEmpty()) {
			// Paranoid check, should not happen...
			monitor.out("\t(looks like the version list is empty, switching to the work item as unique version)");
			result = readWorkItemVersion(wi, repo, pa, complete, wiClient, wiCommon, itemManager, monitor, p, task,
					dir);
			if (null != result)
				return result;
		} else if (complete) {
			for (IWorkItem w : workItems) {
				if (null == w)
					continue;
				result = readWorkItemVersion(w, repo, pa, complete, wiClient, wiCommon, itemManager, monitor, p, task,
						dir);
				if (null != result)
					return result;
			}
		} else {
			IWorkItem v = null;
			for (IWorkItem w : workItems) {
				if (null == w)
					continue;
				v = w;
			}
			// Paranoid check, should not happen...
			monitor.out("\t(looks like the only version(s) in the list are null. Error)");
			if (null == v)
				return "error reading history of work item (only null version(s)) " + wi.getId();
			result = readWorkItemVersion(v, repo, pa, complete, wiClient, wiCommon, itemManager, monitor, p, task, dir);
			if (null != result)
				return result;
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	private static List<IWorkItem> getHistory(IWorkItem wi, IItemManager itemManager, ProgressMonitor monitor)
			throws TeamRepositoryException {
		return itemManager.fetchCompleteStates(itemManager.fetchAllStateHandles(wi, monitor), monitor);
	}

	private static String readWorkItemVersion(IWorkItem w, ITeamRepository repo, IProjectArea pa, boolean complete,
			IWorkItemClient wiClient, IWorkItemCommon wiCommon, IItemManager itemManager, ProgressMonitor monitor,
			Project p, Task task, String dir) throws TeamRepositoryException {

		String result;

		String wiType = w.getWorkItemType();
		monitor.out("\t\tNow reading work item version for " + wiType + " " + w.getId() + " ...");
		TaskType tt = p.getTaskType(wiType);
		if (null == tt) {
			tt = new TaskType(wiType, wiType);
			p.putTaskType(tt);
		} else {
			tt = p.getTaskType(wiType);
		}

		// State
		Identifier<IState> state = w.getState2();
		String stateId = w.getState2().getStringIdentifier();
		IWorkflowInfo workflow = wiCommon.findWorkflowInfo(w, monitor);
		String stateName = workflow.getStateName(state);
		if (stateName == null) {
			stateName = "<null>";
			// return "state name is null (id: " + stateId;
		}
		monitor.out("\t\t\tstate read");
		monitor.out("\t\t\t\tstate id: " + stateId);
		monitor.out("\t\t\t\tstate name: " + stateName);

		XMLString summary = w.getHTMLSummary();
		monitor.out("\t\t\tsummary read");
		monitor.out("\t\t\t\txml text: " + summary.getXMLText());
		monitor.out("\t\t\t\tTYPE: " + IWorkItem.SUMMARY_PROPERTY);

		XMLString description = w.getHTMLDescription();
		monitor.out("\t\t\tdescription read");
		monitor.out("\t\t\t\txml text: " + description.getXMLText());
		monitor.out("\t\t\t\tTYPE: " + IWorkItem.DESCRIPTION_PROPERTY);

		Identifier<IPriority> priority = w.getPriority();
		monitor.out("\t\t\tpriority read");
		monitor.out("\t\t\t\tstring identifier: " + priority.getStringIdentifier());
		monitor.out("\t\t\t\tTYPE: " + IWorkItem.PRIORITY_PROPERTY);

		Identifier<ISeverity> severity = w.getSeverity();
		monitor.out("\t\t\tseverity read");
		monitor.out("\t\t\t\tstring identifier: " + severity.getStringIdentifier());
		monitor.out("\t\t\t\tTYPE: " + IWorkItem.SEVERITY_PROPERTY);

		List<String> tags2 = w.getTags2();
		List<String> tags = new ArrayList<String>();
		for (String t : tags2) {
			tags.add(t);
		}
		monitor.out("\t\t\ttags read");
		monitor.out("\t\t\t\tTYPE: " + IWorkItem.TAGS_PROPERTY);

		ICategory category;
		try {
			category = (ICategory) itemManager.fetchCompleteItem(w.getCategory(), IItemManager.DEFAULT, monitor);
		} catch (TeamRepositoryException e) {
			e.printStackTrace();
			return "can't fetch category from handle";
		}
		monitor.out("\t\t\tcategory read");
		monitor.out("\t\t\t\tTYPE: " + IWorkItem.CATEGORY_PROPERTY);

		IIterationHandle target = w.getTarget();
		monitor.out("\t\t\ttarget iteration read");
		monitor.out("\t\t\t\tTYPE: " + IWorkItem.TARGET_PROPERTY);

		IContributor ownedBy;
		try {
			ownedBy = (IContributor) repo.itemManager().fetchCompleteItem((IContributorHandle) w.getOwner(),
					IItemManager.DEFAULT, monitor);
		} catch (TeamRepositoryException e) {
			e.printStackTrace();
			return "problem retrieving owner for workitem " + task.getId();
		}
		monitor.out("\t\t\towner read");
		monitor.out("\t\t\t\tTYPE: " + IWorkItem.OWNER_PROPERTY);

		IContributor resolvedBy;
		try {
			resolvedBy = (IContributor) repo.itemManager().fetchCompleteItem((IContributorHandle) w.getResolver(),
					IItemManager.DEFAULT, monitor);
		} catch (TeamRepositoryException e) {
			e.printStackTrace();
			return "problem retrieving resolver for workitem " + task.getId();
		}
		Identifier<IResolution> resolution2Id = w.getResolution2();
		String resolution2 = null;
		if (null != resolution2Id) {
			resolution2 = resolution2Id.getStringIdentifier();
		}
		monitor.out("\t\t\tresolution read");
		monitor.out("\t\t\t\tTYPE: " + IWorkItem.RESOLUTION_PROPERTY);

		//
		// TaskVersion
		//
		TaskVersion version = new TaskVersion(//
				w.getItemId().getUuidValue(), //
				task, //
				tt, //
				p.saver().get(stateId), //
				p.saver().get(stateName), //
				p.getMember(w.getModifiedBy().getItemId().getUuidValue()), //
				w.modified(), //
				((null == summary) ? null : p.saver().get(summary.getXMLText())), //
				((null == description) ? null : p.saver().get(description.getXMLText())), //
				((null == priority) ? null : p.saver().get(priority.getStringIdentifier())), //
				p.saver().get(IWorkItem.PRIORITY_PROPERTY), //
				((null == severity) ? null : p.saver().get(severity.getStringIdentifier())), //
				p.saver().get(IWorkItem.SEVERITY_PROPERTY), //
				tags, //
				w.getDueDate(), //
				w.getDuration(), //
				((null == category) ? null : p.getCategory(category.getCategoryId().getInternalRepresentation())), //
				((null == target) ? null : p.getIteration(target.getItemId().getUuidValue())), //
				UserHelper.getM(p, ownedBy), //
				UserHelper.getM(p, resolvedBy), //
				w.getResolutionDate(), //
				resolution2//
		);
		monitor.out("\t\t\tversion created");
		//
		// Attributes
		//
		result = readAttributes(w, repo, pa, wiClient, wiCommon, itemManager, monitor, p, version);
		if (null != result)
			return result;
		monitor.out("\t\t\tcustom attributes read");
		//
		// Comments
		//
		for (IComment comment : w.getComments().getContents()) {
			version.addComment(new Comment(//
					w.getContextId().getUuidValue(), //
					p.getMember(comment.getCreator().getItemId().getUuidValue()), //
					comment.getCreationDate(), //
					(null == comment.getHTMLContent()) ? null : p.saver().get(comment.getHTMLContent().getXMLText())//
			));
		}
		monitor.out("\t\t\tcomments read");
		//
		// Subscribers
		//
		ISubscriptions subscriptions = w.getSubscriptions();
		IContributorHandle[] subscribers = subscriptions.getContents();
		for (IContributorHandle subscriber : subscribers) {
			version.addSubscriber(p.getMember(subscriber.getItemId().getUuidValue()));
		}
		monitor.out("\t\t\tsubscribers read");
		//
		// Save
		//
		monitor.out("\t\t\tabout to add version");
		p.putTaskVersion(version);
		monitor.out("\t\t... just added version for " + version.getTask().getId() + " type "
				+ version.getType().getName() + " modified " + version.getModified());
		return null;
	}

	private static String readAttributes(IWorkItem w, ITeamRepository repo, IProjectArea pa, IWorkItemClient wiClient,
			IWorkItemCommon wiCommon, IItemManager itemManager, ProgressMonitor monitor, Project p,
			TaskVersion version) {

		monitor.out("\t\t\tstarting reading custom attributes");
		IAttribute attribute;
		Attribute a;
		Literal l;
		List<IAttributeHandle> customAttributeHandles = w.getCustomAttributes();
		IFetchResult custom = null;
		try {
			custom = repo.itemManager().fetchCompleteItemsPermissionAware(customAttributeHandles, IItemManager.REFRESH,
					monitor);
			monitor.out("\t\t\t\tcustom");
		} catch (TeamRepositoryException e) {
			e.printStackTrace();
			return "error finding custom attributes for work item version";
		}
		Object vObj;
		for (Object o : custom.getRetrievedItems()) {
			monitor.out("\t\t\t\tnew object...");
			try {
				attribute = (IAttribute) o;
			} catch (Exception e) {
				monitor.out("\t\t\t\t... is not a new attribute: skip");
				continue;
			}
			monitor.out("\t\t\t\t... is a new attribute");
			monitor.out("\t\t\t\t\t" + attribute.getIdentifier());
			a = p.getAttribute(attribute.getIdentifier());
			if (null == a) {
				monitor.out("\t\t\t\t\t\tnot in model: skip");
				continue;
			}
			monitor.out("\t\t\t\t\tfound attribute " + a.getName());
			if (a.isEnum()) {
				monitor.out("\t\t\t\tcustom value is enum");
				@SuppressWarnings("unchecked")
				Identifier<? extends ILiteral> id = (Identifier<? extends ILiteral>) w.getValue(attribute);
				if (null == id) {
					monitor.out("\t\t\t\tcustom value unknown: skip");
				} else {
					l = a.getLiteral(id.getStringIdentifier());
					monitor.out("\t\t\t\tcustom value (" + a.getSourceId() + ":" + a.getType() + "): "
							+ id.getStringIdentifier());
					version.addValue(new Value(//
							attribute.getIdentifier(), //
							a, //
							l//
					));
				}
			} else {
				monitor.out(
						"\t\t\t\tcustom value (" + a.getSourceId() + ":" + a.getType() + "): " + w.getValue(attribute));
				vObj = w.getValue((IAttribute) a.getExternalObject());
				if (vObj instanceof String) {
					version.addValue(new Value(//
							attribute.getIdentifier(), //
							a, //
							(String) vObj//
					));
					monitor.out("\t\t\t\t\t... string \"" + (String) vObj + "\"");
				} else if (vObj instanceof Timestamp) {
					version.addValue(new Value(//
							attribute.getIdentifier(), //
							a, //
							(Timestamp) vObj//
					));
					monitor.out("\t\t\t\t\t... timestamp " + (Timestamp) vObj);
				} else if (vObj instanceof Boolean) {
					version.addValue(new Value(//
							attribute.getIdentifier(), //
							a, //
							(Boolean) vObj//
					));
					monitor.out("\t\t\t\t\t... boolean " + (Boolean) vObj);
				} else {
					monitor.out("\t\t\t\t\t... something else (not taken into account)");
				}
			}
		}
		monitor.out("\t\t\tfinished reading custom attributes");
		return null;
	}

	private static String readLinks(IWorkItem w, ITeamRepository repo, IProjectArea pa, IWorkItemClient wiClient,
			IWorkItemCommon wiCommon, IItemManager itemManager, ProgressMonitor monitor, Project p, Task task,
			String dir) {

		String result;
		IWorkItemReferences references;
		ILink link;
		try {
			references = wiCommon.resolveWorkItemReferences(w, monitor);
		} catch (TeamRepositoryException e) {
			e.printStackTrace();
			return "problem resolving references for workitem " + task.getId();
		}
		IItemHandle referencedItem;
		IWorkItemHandle referencedWorkItem;
		IAttachmentHandle attachmentHandle;
		IAttachment attachment;
		IURIReference referencedURI;
		for (IEndPointDescriptor iEndPointDescriptor : references.getTypes()) {
			if (iEndPointDescriptor.isTarget()) {
				List<IReference> typedReferences = references.getReferences(iEndPointDescriptor);
				for (IReference ref : typedReferences) {
					link = ref.getLink();
					if (ref.isItemReference()) {
						referencedItem = ((IItemReference) ref).getReferencedItem();
						if (referencedItem instanceof IWorkItemHandle) {
							referencedWorkItem = (IWorkItemHandle) referencedItem;
							task.addLink(new Link(//
									link.getItemId().getUuidValue(), //
									referencedWorkItem.getItemId().getUuidValue(), //
									link.getLinkTypeId(), //
									ref.getComment()));
							monitor.out("\t... just added link (type: " + link.getLinkTypeId() + ")"//
									+ "\n\t\tfor " + task.getSourceId() + " (" + task.getId() + ")"//
									+ "\n\t\tto  " + referencedWorkItem.getItemId().getUuidValue());
						} else if (referencedItem instanceof IAttachmentHandle) {
							attachmentHandle = (IAttachmentHandle) ref.resolve();
							try {
								attachment = wiCommon.getAuditableCommon().resolveAuditable(attachmentHandle,
										IAttachment.DEFAULT_PROFILE, monitor);
							} catch (TeamRepositoryException e) {
								e.printStackTrace();
								return "can't resolve attachment handle";
							}
							result = AttachmentHelper.saveAttachment(attachment, dir, monitor);
							if (null != result) {
								return "error saving attachment: " + result;
							}
							task.addAttachment(new Attachment(//
									"" + attachment.getId(), //
									attachment.getName(), //
									attachment.getDescription(), //
									p.getMember(attachment.getCreator().getItemId().getUuidValue()), //
									attachment.getCreationDate(), attachment.getContent().getContentType(), //
									attachment.getContent().getCharacterEncoding()));
							monitor.out("\t... just added attachment for " + task.getId());
						}
					} else if (ref.isURIReference()) {
						referencedURI = ((IURIReference) ref);
						if (!referencedURI.getURI().toString()
								.contains("http://www.ibm.com/support/knowledgecenter/")) {
							task.addArtifact(new Artifact(//
									link.getItemId().getUuidValue(), //
									referencedURI.getURI(), referencedURI.getComment()//
							));
							monitor.out("\t... just added artifact" + " for " + task.getSourceId() + " (" + task.getId()
									+ ")"//
									+ "\n\t\tto " + referencedURI.getURI().toString() + " ("
									+ referencedURI.getComment() + ')');
						}
					}
				}
			}
		}
		return null;
	}

}
