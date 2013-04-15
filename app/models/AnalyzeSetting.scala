package models;

import play.api.libs.json.Json;
import play.api.libs.json.JsValue;
import play.api.libs.json.JsNumber;
import play.api.libs.json.JsBoolean;
import play.api.libs.json.JsArray;
import play.api.i18n.Messages;
import play.api.i18n.Lang;

import scala.io.Source;
import java.io.File;
import java.util.Date;
import jp.co.flect.papertrail.LogAnalyzer;
import jp.co.flect.papertrail.Counter;
import jp.co.flect.papertrail.counter._;

object AnalyzeSetting {
	
	private val counterMap: Map[String, JsonWrapper => Counter] = Map(
		"allLog" -> { option => 
			new AllLogCounter(Messages("counter.allLog"));
		}, "allAccess" -> { option =>
			new AccessCounter(Messages("counter.allAccess"));
		}, "slowRequest" -> { option =>
			val threshold = option.getAsInt("threshold", 1000);
			new SlowRequestCounter(Messages("counter.slowRequest", threshold), threshold);
		}, "slowConnect" -> { option =>
			val threshold = option.getAsInt("threshold", 20);
			new SlowConnectCounter(Messages("counter.slowConnect", threshold), threshold);
		}, "serverError" -> { option =>
			new ServerErrorCounter(Messages("counter.serverError"));
		}, "clientError" -> { option =>
			new ClientErrorCounter(Messages("counter.clientError"));
		}, "herokuError" -> { option =>
			new HerokuErrorCounter(Messages("counter.herokuError"));
		}, "dynoStateChanged" -> { option =>
			new DynoStateChangedCounter(Messages("counter.dynoStateChanged"));
		}, "program" -> { option =>
			new ProgramCounter(Messages("counter.program"));
		}, "responseTime" -> { option =>
			val ret = new ResponseTimeCounter(Messages("counter.responseTime"), Messages("counter.allAccess"));
			option.getAsStringArray("pattern").foreach(ret.addPattern(_));
			option.getAsStringArray("exclude").foreach(ret.addExclude(_));
			
			val includeConnectTime = option.getAsBoolean("includeConnectTime", false);
			ret.setIncludeConnectTime(includeConnectTime);
			ret;
		}, "connectTime" -> { option =>
			new ConnectTimeCounter(Messages("counter.connectTime"));
		}, "slowSQL" -> { option =>
			val includeCopy = option.getAsBoolean("includeCopy", true);
			val duration = option.getAsInt("duration", 50);
			val ret = new PostgresDurationCounter(Messages("counter.slowSQL", duration), Messages("counter.allSQL"));
			
			ret.setIncludeCopy(includeCopy);
			ret.setTargetDuration(duration);
			ret;
		}, "dynoBoot" -> { option =>
			new DynoBootTimeCounter(Messages("counter.dynoBoot"));
		}
	);
	
	def apply(json: String, lastModified: Date) = new AnalyzeSetting(Json.parse(json), lastModified);
	
	lazy val defaultSetting = {
		val file = new File("app/models/defaultSetting.json");
		val source = Source.fromFile(file, "utf-8");
		try {
			apply(source.mkString, new Date(file.lastModified));
		} finally {
			source.close;
		}
	}
	
	private class JsonWrapper(value: JsValue) {
		
		def getAsStringArray(name: String) = {
			(value \ name) match {
				case JsArray(v) => v.map(_.as[String]);
				case _ => Seq[String]();
			}
		}
		
		def getAsInt(name: String, defaultValue: Int) = {
			(value \ name) match {
				case JsNumber(v) => v.intValue;
				case _ => defaultValue;
			}
		}
		
		def getAsBoolean(name: String, defaultValue: Boolean) = {
			(value \ name) match {
				case JsBoolean(v) => v;
				case _ => defaultValue;
			}
		}
	}
}

class AnalyzeSetting(setting: JsValue, val lastModified: Date) {
	
	import AnalyzeSetting._;
	
	def create(implicit lang: Lang) = {
		val ret = new LogAnalyzer();
		(setting \ "counters") match {
			case JsArray(v) => {
				v.foreach { el =>
					val name = el.as[String];
					counterMap.get(name).foreach{ f =>
						val option = new JsonWrapper(setting \ "options" \ name);
						ret.add(f(option));
					}
				}
			}
			case _ =>
		}
		ret;
	}
}
