/*
 * Copyright (c) 2012-2015, Andrea Funto'. All rights reserved. See LICENSE for details.
 */ 

package org.dihedron.demo.portlets.portlet9.actions;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.dihedron.core.streams.Streams;
import org.dihedron.strutlets.ActionContext;
import org.dihedron.strutlets.annotations.Action;
import org.dihedron.strutlets.annotations.In;
import org.dihedron.strutlets.annotations.Invocable;
import org.dihedron.strutlets.annotations.Out;
import org.dihedron.strutlets.annotations.Result;
import org.dihedron.strutlets.annotations.Scope;
import org.dihedron.strutlets.aop.$;
import org.dihedron.strutlets.upload.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andrea Funto'
 */
@Action
public class FileUploadAction {
	/**
	 * The logger.
	 */
	private static final Logger logger = LoggerFactory.getLogger(FileUploadAction.class);
	
	
	@Invocable (
		idempotent = true,
		results = {
			@Result(value = Action.SUCCESS, renderer = "jsp", data = "/html/portlet9/view.jsp")	
		}
	)
	public String render() {
		logger.debug("initialising view...");
		return Action.SUCCESS;
	}
	
	@Invocable(
		idempotent = true,
		results = {
			@Result(value="success_synch", renderer="jsp", data="/html/portlet9/view.jsp"),
			@Result(value="success_asynch", renderer="string", data="result")
		}
	)
	public String onFileUpload ( 
			@In(value="file1", from = Scope.FORM) UploadedFile file1,
			@In(value="file2", from = Scope.FORM) UploadedFile file2,
			@In(value="file3", from = Scope.FORM) UploadedFile file3,
			@Out(value = "result", to = Scope.REQUEST) $<String> result) {
		
		StringBuilder buffer = new StringBuilder("{\n");
		InputStream stream = null;
		logger.debug("processing uploaded files...");
		for(UploadedFile file : new UploadedFile[]{ file1, file2, file3}) {
			if(file != null) {				
				logger.debug("processing file: {}...", file.getFieldName());
				try {
					stream = file.getAsInputStream();
					MessageDigest md = MessageDigest.getInstance("MD5");
					DigestInputStream dis = new DigestInputStream(stream, md);
					long count = Streams.copy(dis, new SinkOutputStream());
					String md5 = new BigInteger(1, md.digest()).toString(16);
					buffer.append("\tfile: {\n");
					buffer.append("\t\tname : '").append(file.getName()).append("',\n");
					buffer.append("\t\tfield: '").append(file.getFieldName()).append("',\n");
					buffer.append("\t\tsize : ").append(count).append(",\n");
					buffer.append("\t\tmd5  : '").append(md5).append("'\n");
					buffer.append("\t},\n");
				} catch (FileNotFoundException e) {
					logger.error("file not found", e);
				} catch (NoSuchAlgorithmException e) {
					logger.error("Md% algorithm not available", e);
				} catch (IOException e) {
					logger.error("error reading data from stream", e);
				} finally {
					safeClose(stream);
				}
			}
		}
		buffer.append("}");
		result.set(buffer.toString());		
		logger.debug("result of upload: \n{}", buffer);
		if(ActionContext.isActionPhase()) {
			return "success_synch";
		} else {
			return "success_asynch";
		}
	}

	private void safeClose(InputStream stream) {
		if(stream != null) {
			try {
				stream.close();
			} catch(IOException e) {
				logger.warn("error closing internal stream", e);
			}
		}
	}
}
