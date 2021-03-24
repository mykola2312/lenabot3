package lenabot3.util;

import java.io.FileNotFoundException;
import java.io.FileReader;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JsonData {
	public static Gson gson = new Gson();
	
	public static JsonObject loadJsonFile(String filename) {
		JsonParser parser = new JsonParser();
		JsonElement json;
		try (FileReader reader = new FileReader(filename)) {
			json = parser.parse(reader);
		} catch (FileNotFoundException e) {
			System.err.println(String.format("[loadJsonFile] File %s not found! (%s)",
					filename, e.getLocalizedMessage()));
			return null;
		} catch (Exception e) {
			System.err.println(String.format("[loadJsonFile] Exception when loading %s (%s)",
					filename, e.getLocalizedMessage()));
			return null;
		}
		return json.getAsJsonObject();
	}
	
	public static String jsonToString(JsonElement json) {
		return gson.toJson(json);
	}
}
