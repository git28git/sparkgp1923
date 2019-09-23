package exam

import com.alibaba.fastjson.{JSON, JSONArray, JSONObject}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

object Demo2 {
  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName(this.getClass.getName).setMaster("local[*]")
    val sc = new SparkContext(conf)
    val jsonstr = sc.textFile("dir/json.txt")
    val jsonbuf = jsonstr.map(t=>{
      val buf = collection.mutable.ListBuffer[String]()
      val parsejson = JSON.parseObject(t)
      val status: Int = parsejson.getIntValue("status")
      if(status == 1){
        val regeocodejson = parsejson.getJSONObject("regeocode")
        if(regeocodejson != null && !regeocodejson.keySet().isEmpty){
          val pois: JSONArray = regeocodejson.getJSONArray("pois")
          if(pois != null && !pois.isEmpty){
            for(item <- pois.toArray){
              if(item.isInstanceOf[JSONObject]){
                val json = item.asInstanceOf[JSONObject]
                buf.append(json.getString("type"))
              }
            }
          }
        }
      }
      buf.mkString(";")
    })
    val res: RDD[(String, Int)] = jsonbuf.flatMap(t => {
      t.split(";").map((_, 1))
    }).reduceByKey(_ + _)

    res.foreach(println)
    sc.stop()
  }
}
