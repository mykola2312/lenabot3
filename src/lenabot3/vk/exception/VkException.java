package lenabot3.vk.exception;

import lenabot3.vk.IVkRequest;

public class VkException extends RuntimeException {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2659044330969100795L;

	enum ExceptionType {
		UnknownException,
		HttpException,
		ParserException,
		VkApiException
	}
	
	private IVkRequest request;
	
	public VkException(String message, IVkRequest request) {
		super(message);
		this.request = request;
	}
	
	public VkException(String message, IVkRequest request, Throwable cause) {
		super(message,cause);
		this.request = request;
	}
	
	public ExceptionType getType() {
		return ExceptionType.UnknownException;
	}
	
	public IVkRequest getRequest() {
		return request;
	}
}
