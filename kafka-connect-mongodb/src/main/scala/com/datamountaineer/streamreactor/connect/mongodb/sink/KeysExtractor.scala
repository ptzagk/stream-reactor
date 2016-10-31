package com.datamountaineer.streamreactor.connect.mongodb.sink

import java.text.SimpleDateFormat
import java.util.TimeZone

import org.apache.kafka.common.config.ConfigException
import org.apache.kafka.connect.data._
import org.json4s.JsonAST._


object KeysExtractor {

  private val ISO_DATE_FORMAT: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  private val TIME_FORMAT: SimpleDateFormat = new SimpleDateFormat("HH:mm:ss.SSSZ")

  ISO_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"))

  def fromStruct(struct: Struct, keys: Set[String]) = {
    keys.map { key =>
      val schema = struct.schema().field(key).schema()
      val value = struct.get(key)

      val v = schema.`type`() match {
        case Schema.Type.INT32 =>
          if (schema != null && Date.LOGICAL_NAME == schema.name) ISO_DATE_FORMAT.format(Date.toLogical(schema, value.asInstanceOf[Int]))
          else if (schema != null && Time.LOGICAL_NAME == schema.name) TIME_FORMAT.format(Time.toLogical(schema, value.asInstanceOf[Int]))
          else value

        case Schema.Type.INT64 =>
          if (Timestamp.LOGICAL_NAME == schema.name) Timestamp.fromLogical(schema, value.asInstanceOf[(java.util.Date)])
          else value

        case Schema.Type.STRING => value.asInstanceOf[CharSequence].toString

        case Schema.Type.BYTES =>
          if (Decimal.LOGICAL_NAME == schema.name) value.asInstanceOf[BigDecimal].toDouble
          else throw new ConfigException(s"Schema.Type.BYTES is not supported for $key.")

        case Schema.Type.ARRAY =>
          throw new ConfigException(s"Schema.Type.ARRAY is not supported for $key.")

        case Schema.Type.MAP => throw new ConfigException(s"Schema.Type.MAP is not supported for $key.")
        case Schema.Type.STRUCT => throw new ConfigException(s"Schema.Type.STRUCT is not supported for $key.")
        case other => throw new ConfigException(s"$other is not supported for $key.")
      }
      key -> v
    }
  }

  def fromMap(map: java.util.Map[String, Any], keys: Set[String]) = {
    keys.map { key =>
      if (!map.containsKey(key)) throw new ConfigException(s"The key $key can't be found")
      val value = map.get(key) match {
        case t: String => t
        case t: Boolean => t
        case t: Int => t
        case t: Long => t
        case t: Double => t
        //type restriction for Mongo
        case t: BigInt => t.toLong
        case t: BigDecimal => t.toDouble
        case other => throw new ConfigException(s"The key $key is not supported for type ${Option(other).map(_.getClass.getName).getOrElse("NULL")}")
      }
      key -> value
    }
  }

  def fromJson(jvalue: JValue, keys: Set[String]) = {
    jvalue match {
      case JObject(children) =>
        children.collect {
          case JField(name, value) if keys.contains(name) =>
            val v = value match {
              case JBool(b) => b
              case JDecimal(d) => d.toDouble //need to do this because of mong
              case JDouble(d) => d
              case JInt(i) => i.toLong //need to do this because of mongo
              case JLong(l) => l
              case JString(s) => s
              case other => throw new ConfigException(s"Field $name is not handled as a key (${other.getClass}). it needs to be a int, long, string, double or decimal")
            }
            name -> v
        }
      case other => throw new ConfigException(s"${other.getClass} is not supported")
    }
  }
}