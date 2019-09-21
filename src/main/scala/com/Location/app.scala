package com.Location

import java.util.Properties

import com.typesafe.config.{Config, ConfigFactory}
import com.util.RptUtil
import org.apache.spark.sql.{SaveMode, SparkSession}
import org.apache.commons.lang3.StringUtils

/**
  * 媒体分析指标
  */
object app {

  def main(args: Array[String]): Unit = {

    if(args.length != 2){
      println("输入目录不正确,重新输入")
      sys.exit()
    }
    val Array(inputPath,appdoc) = args

    val spark: SparkSession = SparkSession.builder()
      .appName("app")
      .master("local[*]")
      .config("spark.serializer","org.apache.spark.serializer.KryoSerializer")
      .getOrCreate()

    val rddMap: collection.Map[String, String] = spark.sparkContext.textFile(appdoc)
      .map(_.split("\\s",-1))
      .filter(_.length >= 5)
      .map(arr => (arr(4),arr(1)))
      .collectAsMap()
    //将数据进行广播
    val broadCast = spark.sparkContext.broadcast(rddMap)

    val df = spark.read.parquet(inputPath)
    val df1 = df.rdd.map(row => {
      // 取媒体相关字段
      var appName = row.getAs[String]("appname")
      if(StringUtils.isBlank(appName)){
        appName = broadCast.value.getOrElse(row.getAs[String]("appid"),"unknow")
      }
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
      (appName,allList)
    }).reduceByKey((list1,list2)=>{
      // list1(1,1,1,1).zip(list2(1,1,1,1))=list((1,1),(1,1),(1,1),(1,1))
      list1.zip(list2).map(t=>t._1+t._2)
    })
      .map(t=>t._1+","+t._2.mkString(","))

    /**
      * 方式一:导入到本地
      */
    //              .saveAsTextFile(outputPath)

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
