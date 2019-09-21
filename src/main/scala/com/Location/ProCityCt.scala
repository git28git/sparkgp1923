package com.Location

import java.util.Properties

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.spark.sql.{DataFrame, SaveMode, SparkSession}

/**
  * 统计省市指标
  */
object ProCityCt {

  def main(args: Array[String]): Unit = {

    if(args.length != 1){
      println("输入目录不正确")
      sys.exit()
    }
    val Array(inputPath) =args

    val spark = SparkSession
      .builder()
      .appName("ct")
      .master("local")
      .config("spark.serializer","org.apache.spark.serializer.KryoSerializer")
      .getOrCreate()

    /**
      * 方法一:输出到本地文件中
      */
    // 获取数据
    val df: DataFrame = spark.read.parquet(inputPath)
    // 注册临时视图
    df.createTempView("log")
    val df2: DataFrame = spark
      .sql("select provincename,cityname,count(*) ct from log group by provincename,cityname")

//    df2.write.partitionBy("provincename","cityname").json("E:\\Data01\\proCity")

    /**
      * 存到Mysql中
      */
    // 通过config配置文件依赖进行加载相关的配置信息
    val load: Config = ConfigFactory.load()
    // 创建Properties对象
    val prop: Properties = new Properties()
    prop.setProperty("user",load.getString("jdbc.user"))
    prop.setProperty("password",load.getString("jdbc.password"))
    // 存储
    df2.write.mode(SaveMode.Append).jdbc(
      load.getString("jdbc.url"),load.getString("jdbc.tableName"),prop)

    spark.stop()
  }
}
