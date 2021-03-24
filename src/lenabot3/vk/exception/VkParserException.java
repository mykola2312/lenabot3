package lenabot3.vk.exception;

import lenabot3.vk.IVkRequest;

public class VkParserException extends VkException {
	/**
	 * 
	 */
	private static final long serialVersionUID = -7996055992121090542L;
	private String content;
	
	public VkParserException(String content,IVkRequest request) {
		super(String.format("Vk Parser Exception - [%s]",content),
			request);
		this.content = content;
	}
	
	public VkParserException(String content,IVkRequest request,
			Throwable cause) {
		super(String.format("Vk Parser Exception - [%s]",content),
			request,cause);
		this.content = content;
	}
	
	public ExceptionType getType() {
		return VkException.ExceptionType.ParserException;
	}
	
	public String getContent() {
		return content;
	}
}
