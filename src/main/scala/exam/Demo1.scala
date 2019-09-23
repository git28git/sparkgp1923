package exam

import com.alibaba.fastjson.{JSON, JSONObject}
import com.util.HttpUtil
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession

object Demo1 {

  def main(args: Array[String]): Unit = {

    if(args.length != 1){
      println("文档不正确")
      sys.exit()
    }
    val Array(inputPath) = args

    val spark = SparkSession.builder()
      .appName("Demo")
      .master("local[*]")
      .getOrCreate()

    val df = spark.read.textFile(inputPath)
    val df1 = df.rdd.map(t => {
      val jsonstr = HttpUtil.get(t)

      val json = JSON.parseObject(jsonstr)
      val status = json.getIntValue("status")

      if(status == 1) {
        return "成功"
      }else{
        return "失败"
      }

      val json1 = json.getJSONObject("regeocode")
      val json2 = json1.getJSONObject("pois")
      val array = json2.getJSONArray("businessarea")

      val result = collection.mutable.ListBuffer[String]()

      for (item <- array.toArray()){
        if(item.isInstanceOf[JSONObject]){
          val json = item.asInstanceOf[JSONObject]
          val name = json.getString("name")
          result.append(name)
        }
      }
      val res = result.mkString(",")
      (res)
    })





    spark.stop()

  }
}
