package com.nus.sparkstreaming
import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.spark.streaming._
import org.apache.spark.streaming.twitter._
import org.apache.spark.streaming.StreamingContext._
import Utilities._
//For multi-threading concepts we use some java stuff
import java.util.concurrent._
//helps in accessing + manipulating a long Integer value
import java.util.concurrent.atomic._
import java.lang.Math

/** Uses thread-safe counters to keep track of the average length of
 *  Tweets in a stream.
 */
object LongestTweet {
  /** Our main function where the action happens */
  def main(args: Array[String]) {

    // Configure Twitter credentials using twitter.txt
    setupTwitter()
    
    // Set up a Spark streaming context named "AverageTweetLength" that runs locally using
    // all CPU cores and one-second batches of data
    val ssc = new StreamingContext("local[*]", "AverageTweetLength", Seconds(1))
    
    // Get rid of log spam (should be called after the context is set up)
    setupLogging()

    // Create a DStream from Twitter using our streaming context
    val tweets = TwitterUtils.createStream(ssc, None)
    
    // Now extract the text of each status update into DStreams using map()
    val statuses = tweets.map(status => status.getText())
   
    // Map this to tweet character lengths.
    val lengths = statuses.map(status => status.length())       
    
    // As we could have multiple processes adding into these running totals
    // at the same time, we'll just Java's AtomicLong class to make sure
    // these counters are thread-safe.    
    
    var longestTweetSeen = new AtomicLong(0)
    var rddMaxVal = new AtomicLong(0)
    
    // In Spark 1.6+, you  might also look into the mapWithState function, which allows
    // you to safely and efficiently keep track of global state with key/value pairs.
    // We'll do that later in the course.
    
    // what is the largest tweet?
    lengths.foreachRDD((rdd, time) => {
      
      var count = rdd.count()
      if (count > 0) {
        totalTweets.getAndAdd(count)
        
        totalChars.getAndAdd(rdd.reduce((x,y) => x + y))
        // we have to repartition to get the rdd in one chunk
        val repartition = rdd.repartition(1).cache()
        rddMaxVal.set(repartition.max())
        if (rddMaxVal.get() > longestTweetSeen.get()) {
          longestTweetSeen.set(rddMaxVal.get())
        }
        
        println("Total tweets: " + totalTweets.get() + 
            " Total characters: " + totalChars.get() + 
            " Average: " + totalChars.get() / totalTweets.get() +
            " Max seen: " + longestTweetSeen.get() 
        )
      }
    })
    
    // Set a checkpoint directory, and kick it all off    
    //ssc.checkpoint("C:/checkpoint/")
    ssc.start()
    ssc.awaitTermination()
  }  
}
