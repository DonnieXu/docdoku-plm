/*
 * DocDoku, Professional Open Source
 * Copyright 2006 - 2013 DocDoku SARL
 *
 * This file is part of DocDokuPLM.
 *
 * DocDokuPLM is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * DocDokuPLM is distributed in the hope that it will be useful,  
 * but WITHOUT ANY WARRANTY; without even the implied warranty of  
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the  
 * GNU Affero General Public License for more details.  
 *  
 * You should have received a copy of the GNU Affero General Public License  
 * along with DocDokuPLM.  If not, see <http://www.gnu.org/licenses/>.  
 */

package com.docdoku.core.document;

import com.docdoku.core.common.User;
import com.docdoku.core.common.Version;
import com.docdoku.core.security.ACL;
import com.docdoku.core.workflow.Workflow;

import javax.persistence.*;
import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.*;

/**
 * This class stands between <a href="DocumentMaster.html">DocumentMaster</a>
 * and <a href="DocumentIteration.html">DocumentIteration</a>.
 * It represents a formal revision of a document and can have an attached workflow.
 * 
 * @author Florent Garin
 * @version 2.0, 11/01/14
 * @since   V2.0
 */
@Table(name="DOCUMENTREVISION")
@IdClass(DocumentRevisionKey.class)
@Entity
@NamedQueries ({
        @NamedQuery(name="DocumentRevision.findWithAssignedTasksForUser", query="SELECT d FROM DocumentRevision d, Task t WHERE t.activity.workflow = d.workflow AND d.workflow IS NOT NULL AND t.worker.login = :assignedUserLogin AND d.documentMasterWorkspaceId = :workspaceId"),
        @NamedQuery(name="DocumentRevision.findWithOpenedTasksForUser", query="SELECT d FROM DocumentRevision d, Task t WHERE t.activity.workflow = d.workflow AND d.workflow IS NOT NULL AND t.worker.login = :assignedUserLogin AND d.documentMasterWorkspaceId = :workspaceId AND t.status = com.docdoku.core.workflow.Task.Status.IN_PROGRESS"),
        @NamedQuery(name="DocumentRevision.findByReference", query="SELECT d FROM DocumentRevision d WHERE d.documentMasterId LIKE :id AND d.documentMasterWorkspaceId = :workspaceId"),
        @NamedQuery(name="DocumentRevision.countInWorkspace", query="SELECT COUNT(d) FROM DocumentRevision d WHERE d.documentMasterWorkspaceId = :workspaceId"),
        @NamedQuery(name="DocumentRevision.findByWorkspace.filterUserACLEntry", query="SELECT dr FROM DocumentRevision dr WHERE dr.documentMasterWorkspaceId = :workspaceId and (dr.acl is null or exists(SELECT au from ACLUserEntry au WHERE au.principal = :user AND au.permission not like com.docdoku.core.security.ACL.Permission.FORBIDDEN AND au.acl = dr.acl)) AND dr.location.completePath NOT LIKE :excludedFolders ORDER BY dr.documentMasterId ASC"),
        @NamedQuery(name="DocumentRevision.countByWorkspace.filterUserACLEntry", query="SELECT count(dr) FROM DocumentRevision dr WHERE dr.documentMasterWorkspaceId = :workspaceId and (dr.acl is null or exists(SELECT au from ACLUserEntry au WHERE au.principal = :user AND au.permission not like com.docdoku.core.security.ACL.Permission.FORBIDDEN AND au.acl = dr.acl)) AND dr.location.completePath NOT LIKE :excludedFolders")
})
public class DocumentRevision implements Serializable, Comparable<DocumentRevision>, Cloneable {


    @Id
    @ManyToOne(optional=false, fetch=FetchType.EAGER)
    @JoinColumns({
            @JoinColumn(name="DOCUMENTMASTER_ID", referencedColumnName="ID"),
            @JoinColumn(name="WORKSPACE_ID", referencedColumnName="WORKSPACE_ID")
    })
    private DocumentMaster documentMaster;

    @Column(length=10)
    @Id
    private String version="";

    @ManyToOne(fetch=FetchType.EAGER)
    @JoinColumns({
        @JoinColumn(name="AUTHOR_LOGIN", referencedColumnName="LOGIN"),
        @JoinColumn(name="AUTHOR_WORKSPACE_ID", referencedColumnName="WORKSPACE_ID")
    })
    private User author;

    @Temporal(TemporalType.TIMESTAMP)
    private Date creationDate;

    private String title;

    @Lob
    private String description;

    @OneToMany(mappedBy = "documentRevision", cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    @OrderBy("iteration ASC")
    private List<DocumentIteration> documentIterations = new ArrayList<>();

    @ManyToOne(fetch=FetchType.EAGER)
    @JoinColumns({
        @JoinColumn(name="CHECKOUTUSER_LOGIN", referencedColumnName="LOGIN"),
        @JoinColumn(name="CHECKOUTUSER_WORKSPACE_ID", referencedColumnName="WORKSPACE_ID")
    })
    private User checkOutUser;

    @Temporal(TemporalType.TIMESTAMP)
    private Date checkOutDate;

    @OneToOne(orphanRemoval=true, cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    private Workflow workflow;

    @OneToMany(orphanRemoval=true, cascade= CascadeType.ALL, fetch= FetchType.EAGER)
    @JoinTable(name="DOCUMENT_ABORTED_WORKFLOW",
        inverseJoinColumns={
            @JoinColumn(name="WORKFLOW_ID", referencedColumnName="ID")
        },
        joinColumns={
            @JoinColumn(name="DOCUMENTMASTER_ID", referencedColumnName="DOCUMENTMASTER_ID"),
            @JoinColumn(name="DOCUMENTREVISION_VERSION", referencedColumnName="VERSION"),
            @JoinColumn(name="DOCUMENTMASTER_WORKSPACE_ID", referencedColumnName="WORKSPACE_ID")
    })
    private List<Workflow> abortedWorkflows=new ArrayList<>();

    @ManyToOne(fetch=FetchType.EAGER)
    private Folder location;

    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(name="DOCUMENTREVISION_TAG",
    inverseJoinColumns={
        @JoinColumn(name="TAG_LABEL", referencedColumnName="LABEL"),
        @JoinColumn(name="TAG_WORKSPACE_ID", referencedColumnName="WORKSPACE_ID")
    },
    joinColumns={
        @JoinColumn(name="DOCUMENTMASTER_ID", referencedColumnName="DOCUMENTMASTER_ID"),
        @JoinColumn(name="DOCUMENTREVISION_VERSION", referencedColumnName="VERSION"),
        @JoinColumn(name="DOCUMENTMASTER_WORKSPACE_ID", referencedColumnName="WORKSPACE_ID")
    })
    private Set<Tag> tags=new HashSet<Tag>();


    @Column(name = "DOCUMENTMASTER_ID", nullable = false, insertable = false, updatable = false)
    private String documentMasterId="";

    @Column(name = "WORKSPACE_ID", nullable = false, insertable = false, updatable = false)
    private String documentMasterWorkspaceId="";


    @OneToOne(orphanRemoval = true, cascade=CascadeType.ALL, fetch=FetchType.EAGER)
    private ACL acl;

    private boolean publicShared;

    public DocumentRevision() {
    }

    public DocumentRevision(DocumentMaster pDocumentMaster,
                        String pStringVersion,
                        User pAuthor) {
        this(pDocumentMaster);
        version=pStringVersion;
        author = pAuthor;
    }

    public DocumentRevision(DocumentMaster pDocumentMaster,
                        Version pVersion,
                        User pAuthor) {
        this(pDocumentMaster);
        version=pVersion.toString();
        author = pAuthor;
    }

    public DocumentRevision(DocumentMaster pDocumentMaster, User pAuthor) {
        this(pDocumentMaster);
        version = new Version().toString();
        author = pAuthor;
    }

    private DocumentRevision(DocumentMaster pDocumentMaster) {
        setDocumentMaster(pDocumentMaster);
    }

    public void setDocumentMaster(DocumentMaster documentMaster) {
        this.documentMaster = documentMaster;
        documentMasterId=documentMaster.getId();
        documentMasterWorkspaceId=documentMaster.getWorkspaceId();
    }

    @XmlTransient
    public DocumentMaster getDocumentMaster() {
        return documentMaster;
    }

    public DocumentRevisionKey getKey() {
        return new DocumentRevisionKey(getDocumentMasterKey(), version);
    }

    public boolean isCheckedOut() {
        return (checkOutUser != null);
    }

    public User getCheckOutUser() {
        return checkOutUser;
    }

    public void setCheckOutUser(User pCheckOutUser) {
        checkOutUser = pCheckOutUser;
    }

    public Date getCheckOutDate() {
        return checkOutDate;
    }

    public void setCheckOutDate(Date pCheckOutDate) {
        checkOutDate = pCheckOutDate;
    }

    public void setAuthor(User pAuthor) {
        author = pAuthor;
    }

    public User getAuthor() {
        return author;
    }

    public void setCreationDate(Date pCreationDate) {
        creationDate = pCreationDate;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public void setWorkflow(Workflow pWorkflow) {
        workflow = pWorkflow;
    }

    public String getLifeCycleState() {
        if (workflow != null)
            return workflow.getLifeCycleState();
        else
            return null;
    }

    public boolean hasWorkflow() {
        return (workflow != null);
    }

    public ACL getACL() {
        return acl;
    }

    public void setACL(ACL acl) {
        this.acl = acl;
    }

    public List<Workflow> getAbortedWorkflows() {
        return abortedWorkflows;
    }

    public void addAbortedWorkflows(Workflow abortedWorkflow) {
        this.abortedWorkflows.add(abortedWorkflow);
    }

    public boolean isCheckedOutBy(String pUser) {
        return (checkOutUser != null && checkOutUser.getLogin().equals(pUser));
    }

    public DocumentIteration createNextIteration(User pUser){
        DocumentIteration lastDoc=getLastIteration();
        int iteration = lastDoc==null?1:lastDoc.getIteration() + 1;
        DocumentIteration doc = new DocumentIteration(this,iteration,pUser);
        documentIterations.add(doc);
        return doc;
    }

    public void setDocumentIterations(List<DocumentIteration> documentIterations) {
        this.documentIterations = documentIterations;
    }

    public List<DocumentIteration> getDocumentIterations() {
        return documentIterations;
    }

    public DocumentIteration getLastIteration() {
        int index = documentIterations.size()-1;
        if(index < 0)
            return null;
        else
            return documentIterations.get(index);
    }

    public DocumentIteration removeLastIteration() {
        int index = documentIterations.size()-1;
        if(index < 0)
            return null;
        else
            return documentIterations.remove(index);
    }

    public DocumentIteration getIteration(int pIteration) {
        return documentIterations.get(pIteration-1);
    }

    public int getNumberOfIterations() {
        return documentIterations.size();
    }

    public void setDescription(String pDescription) {
        description = pDescription;
    }

    public String getDescription() {
        return description;
    }

    public DocumentMasterKey getDocumentMasterKey() {
        return documentMaster==null?new DocumentMasterKey("",""):documentMaster.getKey();
    }

    public String getWorkspaceId() {
        return documentMaster==null?"":documentMaster.getWorkspaceId();
    }

    public String getId() {
        return documentMaster==null?"":documentMaster.getId();
    }


    public String getDocumentMasterId() {
        return documentMasterId;
    }

    public String getDocumentMasterWorkspaceId() {
        return documentMasterWorkspaceId;
    }


    public boolean isPublicShared() {
        return publicShared;
    }

    public void setPublicShared(boolean publicShared) {
        this.publicShared = publicShared;
    }


    public void setTitle(String pTitle) {
        title = pTitle;
    }

    public String getTitle() {
        return title;
    }




    public boolean isCheckedOutBy(User pUser) {
        return isCheckedOutBy(pUser.getLogin());
    }



    public Set<Tag> getTags() {
        return tags;
    }


    public void setTags(Set<Tag> pTags) {
        tags.retainAll(pTags);
        pTags.removeAll(tags);
        tags.addAll(pTags);
    }
    
    public boolean addTag(Tag pTag){
        return tags.add(pTag);
    }
    
    public boolean removeTag(Tag pTag){
        return tags.remove(pTag);
    }
    
    @Override
    public String toString() {
        return documentMaster.getId() + "-" + version;
    }
    
    @Override
    public boolean equals(Object pObj) {
        if (this == pObj) {
            return true;
        }
        if (!(pObj instanceof DocumentRevision))
            return false;
        DocumentRevision docR = (DocumentRevision) pObj;
        return ((docR.getId().equals(getId())) && (docR.getWorkspaceId().equals(getWorkspaceId())) && (docR.version.equals(version)));
        
    }
    
    @Override
    public int hashCode() {
        int hash = 1;
	    hash = 31 * hash + getWorkspaceId().hashCode();
	    hash = 31 * hash + getId().hashCode();
        hash = 31 * hash + version.hashCode();
	    return hash;
    }

    public int compareTo(DocumentRevision pDocR) {
        int wksComp = getWorkspaceId().compareTo(pDocR.getWorkspaceId());
        if (wksComp != 0)
            return wksComp;
        int idComp = getId().compareTo(pDocR.getId());
        if (idComp != 0)
            return idComp;
        else
            return version.compareTo(pDocR.version);
    }
    
    public Folder getLocation() {
        return location;
    }
    
    public void setLocation(Folder pLocation) {
        location = pLocation;
    }

    /**
     * perform a deep clone operation
     */
    @Override
    public DocumentRevision clone() throws InternalError{
        DocumentRevision clone;
        try {
            clone = (DocumentRevision) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
        //perform a deep copy
        List<DocumentIteration> clonedDocumentIterations = new ArrayList<>();
        for (DocumentIteration document : documentIterations) {
            DocumentIteration clonedDocument=document.clone();
            clonedDocument.setDocumentRevision(clone);
            clonedDocumentIterations.add(clonedDocument);
        }
        clone.documentIterations = clonedDocumentIterations;
        
        if(workflow !=null)
            clone.workflow = workflow.clone();

        //perform a deep copy
        List<Workflow> clonedAbortedWorkflows =new ArrayList<>();
        for (Workflow abortedWorkflow : abortedWorkflows) {
            Workflow clonedWorkflow=abortedWorkflow.clone();
            clonedAbortedWorkflows.add(clonedWorkflow);
        }
        clone.abortedWorkflows = clonedAbortedWorkflows;

        if(acl !=null)
            clone.acl = acl.clone();

        clone.tags = new HashSet<>(tags);
        
        if(creationDate!=null)
            clone.creationDate = (Date) creationDate.clone();
        
        if(checkOutDate!=null)
            clone.checkOutDate = (Date) checkOutDate.clone();
        
        return clone;
    }
    
}