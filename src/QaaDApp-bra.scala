import QaaD._
import java.io._
import scala.io.Source

def readDataset(inputName: String): RDD[Any] = {
	def arrayToTuple[A <: Object](array:Array[A]): Any = {
		val c = Class.forName("scala.Tuple" + array.size)
		c.getConstructors.apply(0).newInstance(array:_*).asInstanceOf[Any]
	}

	sc.textFile(inputName).map { line =>
		val sLine = line.replace("(", "").replace(")", "").split(",")
		val tuple = arrayToTuple(sLine.slice(1, sLine.size))
		tuple
	}
}

def getDist(fromLat: Float, fromLng: Float, toLat: Float, toLng: Float): Float = {
	val AVERAGE_RADIUS_OF_EARTH_KM = 6371f
	val latDistance = Math.toRadians(fromLat - toLat)
	val lngDistance = Math.toRadians(fromLng - toLng)
	val sinLat = Math.sin(latDistance / 2)
	val sinLng = Math.sin(lngDistance / 2)
	val a = sinLat * sinLat +
	(Math.cos(Math.toRadians(fromLat)) *
			Math.cos(Math.toRadians(toLat)) *
			sinLng * sinLng)
	val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
	val dist = AVERAGE_RADIUS_OF_EARTH_KM * c
	1.0f * dist.toFloat
}

try {
  val rddOrders = readDataset(s"/root/qbatcher/datasets/synthetic-brazilian-ecommerce/num-rows-${numRows}/orders.csv")
  val rddOrderItems = readDataset(s"/root/qbatcher/datasets/synthetic-brazilian-ecommerce/num-rows-${numRows}/order_items.csv")
  val rddProducts = readDataset(s"/root/qbatcher/datasets/synthetic-brazilian-ecommerce/num-rows-${numRows}/products.csv")
  val rddCustomers = readDataset(s"/root/qbatcher/datasets/synthetic-brazilian-ecommerce/num-rows-${numRows}/customers.csv")
  val rddSellers = readDataset(s"/root/qbatcher/datasets/synthetic-brazilian-ecommerce/num-rows-${numRows}/sellers.csv")
  val rddGeolocation = readDataset(s"/root/qbatcher/datasets/synthetic-brazilian-ecommerce/num-rows-${numRows}/geolocation.csv")
  val rddOrderPayments = readDataset(s"/root/qbatcher/datasets/synthetic-brazilian-ecommerce/num-rows-${numRows}/order_payments.csv")
  val rddOrderReviews = readDataset(s"/root/qbatcher/datasets/synthetic-brazilian-ecommerce/num-rows-${numRows}/order_reviews.csv")
  rddOrders.collect()
  rddOrderItems.collect()
  rddProducts.collect()
  rddCustomers.collect()
  rddSellers.collect()
  rddGeolocation.collect()
  rddOrderPayments.collect()
  rddOrderReviews.collect()
  var count = (-32) * foldId
  var curQueryId = 0
  for (line <- Source.fromFile(s"../querysets/brazilian-ecommerce/param/param-num-rows-${numRows}.csv").getLines()) {
    count += 1
    if (count > 1 && count <= numQueries + 1) {
			val sLine = line.split(",")
			val sellerId = sLine(10)
			val startTime = sLine(3).toLong
			val endTime = startTime + 2592000L
      val distance = getDist(sLine(26).toFloat, sLine(27).toFloat, sLine(39).toFloat, sLine(40).toFloat)
      val volume = sLine(33).toFloat * sLine(34).toFloat * sLine(35).toFloat
      val customerCity = sLine(24)
      val customerState = sLine(25)
      val sellerCity = sLine(37)
      val sellerState = sLine(38)
      val reviewScore = sLine(19).toFloat
      val orderStatus = sLine(2)
      val deliveredTime = sLine(6).toLong
      val customerZipCodePrefix = sLine(23)
      val sellerZipCodePrefix = sLine(36)
      val reviewAnswerTime = sLine(21).toLong
      val weight = sLine(32).toFloat
			val paramMap = Map[String, Any]("sellerId" -> sellerId,
				"startTime" -> startTime, 
				"endTime" -> endTime,
        "distance" -> distance,
        "weight" -> weight,
        "customerCity" -> customerCity,
        "sellerCity" -> sellerCity,
        "customerState" -> customerState,
        "sellerState" -> sellerState,
        "reviewScore" -> reviewScore,
        "orderStatus" -> orderStatus,
        "deliveredTime" -> deliveredTime,
        "customerZipCodePrefix" -> customerZipCodePrefix,
        "sellerZipCodePrefix" -> sellerZipCodePrefix,
				"reviewAnswerTime" -> reviewAnswerTime, 
        "volume" -> volume,
        "numRows" -> numRows)
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp1(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp2(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp3(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp4(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp5(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp6(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp7(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp8(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp9(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp10(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp11(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp12(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp13(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp14(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp15(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp16(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp17(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp18(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp19(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp20(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp21(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp22(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp23(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp24(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp25(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp26(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp27(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp28(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp29(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp30(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp31(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp32(paramMap).run()
			}
			curQueryId += 1
			if (queryIds.contains(curQueryId)) {
				new DashboardApp33(paramMap).run()
			}
			if (count == numQueries + 1) exec()
    }
  }
} catch {
  case ex: Exception => println(ex)
}

def exec(): Unit = { 
  scheduler.run()
  val resultMicroRddIdList = QaaD.microRddDag.keys.filter { microRddId =>
    QaaD.microRddDag(microRddId).childList.size == 0
  }.toList
  val resultRddIdSet = resultMicroRddIdList.map(microRddId => QaaD.rddIdByMicroRddId(microRddId)).filter(rddId => rddId >= 0).toSet
  val result = sc.union(resultRddIdSet.map(rddId =>
    QaaD.rddArr.result()(rddId).filter { case (microRddId, record) =>
      resultMicroRddIdList.contains(microRddId)
    }).toSeq).collect().toList
  println("Results for the given query set: " + result)
  System.exit(0)
}
  
System.exit(0)
