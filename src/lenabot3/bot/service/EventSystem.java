package lenabot3.bot.service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import lenabot3.bot.Command;
import lenabot3.bot.Service;

public class EventSystem extends Service {
	public static final String EVENTSYSTEM_SERVICE = "EventSystem";
	
	private HashMap<IEvent,Long> events = new HashMap<IEvent,Long>();
	
	public EventSystem() {
		super(EVENTSYSTEM_SERVICE);
	}
	
	public boolean start() {
		return true;
	}
	
	public void stop() {}
	
	public void run() {
		for (Map.Entry<IEvent, Long> entry : events.entrySet()) {
			if (System.currentTimeMillis() >= entry.getValue()) {
				IEvent event = entry.getKey();
				System.out.printf("[EventSystem] %s fire\n",event.getName());
				event.fire(getBot());
				entry.setValue(System.currentTimeMillis() + event.getInterval());
			}
		}
	}
	
	public boolean load() {
		setUpdateInterval(1000L);
		return true;
	}
	
	public void save() {}
	
	public boolean processCommand(Command command) {
		if (command.getName().equals("события") 
				&& command.getBot().isAdmin(command.getUser())) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			
			pw.println("[EventSystem] события\n\n");
			for (Map.Entry<IEvent, Long> entry : events.entrySet()) {
				IEvent event = entry.getKey();
				DateFormat dateFormat = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
				pw.printf("\"%s\" (%d мс) - %s\n",event.getName(),event.getInterval(),
						dateFormat.format(new Date(entry.getValue())));
			}
			
			command.getBot().sendMessage(command.getPeerId(), sw.toString());
		}
		return false;
	}
	
	public void registerEvent(IEvent event) {
		events.put(event, System.currentTimeMillis() + event.getInterval());
		if(event.getInterval() < getUpdateInterval()) {
			setUpdateInterval(event.getInterval());
		}
	}
}
