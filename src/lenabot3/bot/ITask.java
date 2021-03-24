package lenabot3.bot;

public interface ITask extends Runnable {
	public boolean start();
	public void stop();
}
