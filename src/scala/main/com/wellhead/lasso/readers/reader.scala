package com.wellhead.lasso
import org.slf4j.{Logger,LoggerFactory}
import java.util.{StringTokenizer, LinkedList,ArrayList, List}
import java.io.{File, FileReader, BufferedReader, LineNumberReader}
import scala.collection.mutable.{Queue,ListBuffer}
import scala.collection.jcl.Conversions._
import java.lang.Double
import Util._

class LasFileParser extends LasReader {

  private val header_prefixes = Map("~V" -> "VersionHeader",
				    "~W" -> "WellHeader",
				    "~C" -> "CurveHeader",
				    "~P" -> "ParameterHeader")

  private val white_space = "\n\r\t "
  
  private val logger = LoggerFactory.getLogger("LasFileParser")

  override def canRead(protocol:String):java.lang.Boolean = protocol == "file"

  override def readLasFile(path:String):LasFile = {
    parseLasFile(new File(path))
  }

  override def readCurve(path:String):Curve = {
    throw new UnsupportedOperationException("oops, haven't implemented this yet")
  }

  private def parseLasFile(path:String):LasFile = {
    parseLasFile(new File(path))
  }

  private def parseLasFile(file:File):LasFile = {
    logger.info("Parsing {}", file.getName)
    var reader = new LineNumberReader(new FileReader(file))
    try {
      val headers = parseHeaders(reader)
      val curveHeader = headers.find(_.getType == "CurveHeader").get
      val (index, curves) = parseCurves(curveHeader, reader)
      WHLasFile(file.getName, headers, index, curves)
    } catch {
      case e => 
	logger.error("parser failed at line number : " + reader.getLineNumber)
        throw e
    }	    
    finally {
      reader.close()
    }
  }

  private def parseCurves(curveHeader:Header,reader:LineNumberReader) = {
    val descriptors = curveHeader.getDescriptors
    val n = descriptors.size
    val data:Queue[Double] = parseData(reader)
    val rows = data.size / n
    val cdatas = Util.repeat(n, () => new ArrayList[Double](rows))
    while(!data.isEmpty){
      (0 until n).foreach(i => {
	val d = data.dequeue
	cdatas(i).add(d)
      })
    }	
    val index = WHCurve(descriptors(0), null, cdatas(0))
    val curves = new ArrayList[Curve](n)
    (1 until n).foreach(i => {
      curves.add(WHCurve(descriptors(i), index, cdatas(i)))
    })
    (index, curves)
  }

  private def parseHeaders(reader:LineNumberReader):List[Header] = {
    val line = nextLine(reader)
    val headers = new ArrayList[Header](4)
    var prefix = line.trim.take(2)
    for(i <- 0 until 4){
      val (prefix_line, descriptors) = parseDescriptors(reader)
      val htype = header_prefixes(prefix)
      headers += WHHeader(htype, prefix, descriptors)
      prefix = prefix_line.trim.take(2)
    }
    return headers
  }

  private def parseData(reader:LineNumberReader):Queue[Double] = {
    val data = new Queue[Double]()
    while(reader.ready()) {
      val row = reader.readLine().trim().replaceAll("\t", " ")
      var tokenizer:StringTokenizer = new StringTokenizer(row, " ")
      while(tokenizer.hasMoreTokens){
	val token = tokenizer.nextToken()
	val dval = java.lang.Double.valueOf(token).doubleValue
	data += dval
      }
    }
    return data
  }    
  
  private def parseDescriptors(reader:LineNumberReader) = {
    var continue = true
    val descriptors = new ArrayList[Descriptor]
    var next_prefix:String = null
    while(reader.ready() && continue){
      val line = nextLine(reader)
      if(hasPrefix(line)){
	continue = false
	next_prefix = line
      }
      else {
	descriptors.add(parseDescriptor(line))
      }
    }
    (next_prefix, descriptors)
  }

  private def parseDescriptor(line1:String):Descriptor = {
    val dot = line1.indexOf('.')
    val mnemonic = line1.substring(0, dot)
    val line2 = line1.substring(dot+1)
    val space = line2.indexOf(' ')
    val unit = line2.substring(0, space)
    val line3 = line2.substring(space+1)
    val colon = line3.lastIndexOf(':')
    val data = line3.substring(0, colon)
    val description = line3.substring(colon+1)
    return WHDescriptor(mnemonic.trim, unit.trim, data.trim, description.trim)
  }

  private def isComment(line:String) = line.startsWith("#") || line.trim.startsWith("#")

  private def hasPrefix(line:String) = {
    ((header_prefixes.keySet).exists(line.startsWith) || 
     line.startsWith("~A"))
  }

  private def nextLine(reader:LineNumberReader) = {
    var line = reader.readLine()
    while(isComment(line) || line.trim == ""){
      line = reader.readLine()
    }
    line
  }

}
