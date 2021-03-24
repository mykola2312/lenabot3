package lenabot3.vk.exception;

import lenabot3.vk.IVkRequest;

public class VkHttpException extends VkException {
	/**
	 * 
	 */
	private static final long serialVersionUID = -2905105889984846743L;
	private int httpCode;
	private String httpBody;
	
	public VkHttpException(int httpCode,String httpBody,IVkRequest request) {
		super(String.format("Vk HTTP Exception %d - <<%s>>", 
				httpCode,httpBody),request);
		this.httpCode = httpCode;
		this.httpBody = httpBody;
	}
	
	public ExceptionType getType() {
		return VkException.ExceptionType.HttpException;
	}
	
	public int getCode() {
		return httpCode;
	}
	
	public String getBody() {
		return httpBody;
	}
}
