package com.Location

import java.util.Properties

import com.typesafe.config.{Config, ConfigFactory}
import com.util.RptUtil
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{DataFrame, SaveMode, SparkSession}

object locationRpt {

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
    // 获取数据
    val df: DataFrame = spark.read.parquet(inputPath)

    val df1: RDD[String] = df.rdd.map(row=>{
      // 根据指标的字段获取数据
      // REQUESTMODE	PROCESSNODE	ISEFFECTIVE	ISBILLING	ISBID	ISWIN	ADORDERID WinPrice adpayment
      val requestmode = row.getAs[Int]("requestmode")
      val processnode = row.getAs[Int]("processnode")
      val iseffective = row.getAs[Int]("iseffective")
      val isbilling = row.getAs[Int]("isbilling")
      val isbid = row.getAs[Int]("isbid")
      val iswin = row.getAs[Int]("iswin")
      val adordeerid = row.getAs[Int]("adorderid")
      val winprice = row.getAs[Double]("winprice")
      val adpayment = row.getAs[Double]("adpayment")
      // 处理请求数
      val rptList = RptUtil.ReqPt(requestmode,processnode)
      // 处理展示点击
      val clickList = RptUtil.clickPt(requestmode,iseffective)
      // 处理广告
      val adList = RptUtil.adPt(iseffective,isbilling,isbid,iswin,adordeerid,winprice,adpayment)
      // 所有指标
      val allList:List[Double] = rptList ++ clickList ++ adList
      ((row.getAs[String]("provincename"),row.getAs[String]("cityname")),allList)
    }).reduceByKey((list1,list2)=>{
      // list1(1,1,1,1).zip(list2(1,1,1,1))=list((1,1),(1,1),(1,1),(1,1))
      list1.zip(list2).map(t=>t._1+t._2)
    })
      .map(t=>t._1+","+t._2.mkString(","))

    /**
      * 方式一:导入到本地
      */
    //      .saveAsTextFile(outputPath)

    /**
      * 方式二: 存储到mysql中
      */
    // 通过config配置文件依赖进行加载相关的配置信息
    val load: Config = ConfigFactory.load()
    // 创建Properties对象
    val prop: Properties = new Properties()
    prop.setProperty("user",load.getString("jdbc.user"))
    prop.setProperty("password",load.getString("jdbc.password"))
    // 存储
    import spark.implicits._
    df1.toDF().write.mode(SaveMode.Append).jdbc(
      load.getString("jdbc.url"),load.getString("jdbc.tableName"),prop)


  }
}
