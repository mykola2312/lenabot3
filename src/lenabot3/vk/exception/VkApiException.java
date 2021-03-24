package lenabot3.vk.exception;

import lenabot3.vk.IVkRequest;

public class VkApiException extends VkException {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6408968065832634307L;
	private int vkErrorCode;
	private String vkErrorMessage;
	
	public VkApiException(int vkErrorCode,String vkErrorMessage,
			IVkRequest vkRequest) {
		super(String.format("VK API Error %d - %s", vkErrorCode,vkErrorMessage),
				vkRequest);
		this.vkErrorCode = vkErrorCode;
		this.vkErrorMessage = vkErrorMessage;
	}
	
	public ExceptionType getType() {
		return ExceptionType.VkApiException;
	}
	
	public int getVkErrorCode() {
		return vkErrorCode;
	}
	
	public String getVkErrorMessage() {
		return vkErrorMessage;
	}
}
