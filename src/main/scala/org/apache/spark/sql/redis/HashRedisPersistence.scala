package org.apache.spark.sql.redis

import java.lang.{Boolean => JBoolean, Byte => JByte, Double => JDouble, Float => JFloat, Long => JLong}
import java.nio.charset.StandardCharsets.UTF_8
import java.util.{Map => JMap}

import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema
import org.apache.spark.sql.types._
import redis.clients.jedis.{Pipeline, Response}

import scala.collection.JavaConverters._

/**
  * @author The Viet Nguyen
  */
class HashRedisPersistence extends RedisPersistence[JMap[Array[Byte], Array[Byte]]] {

  override def save(pipeline: Pipeline,
                    key: Array[Byte], value: JMap[Array[Byte], Array[Byte]]): Unit =
    pipeline.hmset(key, value)

  override def load(pipeline: Pipeline,
                    key: Array[Byte]): Response[JMap[Array[Byte], Array[Byte]]] =
    pipeline.hgetAll(key)

  override def encodeRow(value: Row): JMap[Array[Byte], Array[Byte]] = {
    val fields = value.schema.fields.map(_.name)
    val kvMap = value.getValuesMap[Any](fields)
    kvMap.map { case (k, v) =>
      k.getBytes(UTF_8) -> String.valueOf(v).getBytes(UTF_8)
    }.asJava
  }

  override def decodeRow(value: JMap[Array[Byte], Array[Byte]], schema: => StructType,
                         inferSchema: Boolean): Row = {
    val actualSchema = if (!inferSchema) schema else {
      val fields = value.keySet().asScala
        .map(new String(_, UTF_8))
        .map(StructField(_, StringType))
        .toArray
      StructType(fields)
    }
    val fieldsValue = parseFields(value, actualSchema)
    new GenericRowWithSchema(fieldsValue, actualSchema)
  }

  private def parseFields(value: JMap[Array[Byte], Array[Byte]], schema: StructType): Array[Any] =
    schema.fields.map { field =>
      val fieldName = field.name
      val fieldNameBytes = fieldName.getBytes(UTF_8)
      val fieldValueBytes = value.get(fieldNameBytes)
      val fieldValueStr = new String(fieldValueBytes, UTF_8)
      parseValue(field.dataType, fieldValueStr)
    }

  private def parseValue(dataType: DataType, fieldValueStr: String): Any =
    dataType match {
      case ByteType => JByte.parseByte(fieldValueStr)
      case IntegerType => Integer.parseInt(fieldValueStr)
      case LongType => JLong.parseLong(fieldValueStr)
      case FloatType => JFloat.parseFloat(fieldValueStr)
      case DoubleType => JDouble.parseDouble(fieldValueStr)
      case BooleanType => JBoolean.parseBoolean(fieldValueStr)
      case _ => fieldValueStr
    }
}