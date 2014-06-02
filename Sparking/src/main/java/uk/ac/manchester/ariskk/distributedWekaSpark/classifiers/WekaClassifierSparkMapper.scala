package uk.ac.manchester.ariskk.distributedWekaSpark.classifiers

import weka.core.Instances
import weka.core.Utils._
import weka.classifiers.Classifier
import weka.distributed.WekaClassifierMapTask
import weka.distributed.CSVToARFFHeaderMapTask
import weka.distributed.CSVToARFFHeaderReduceTask._
import weka.distributed.CSVToARFFHeaderReduceTask
import weka.classifiers.SingleClassifierEnhancer
import weka.core.Utils

/**Mapper implementation for WekaClassifierSpark job 
 * 
 *  @author Aris-Kyriakos Koliopoulos (ak.koliopoulos {[at]} gmail {[dot]} com)
 *  
 *  Trains and returns a classifier on a dataset partition 
 *  @param classAtt is the class index
 *  @param metaLearner is the requested metaLearner. if 'default' Vote will be used
 *  @param classifierToTrain is the requested base classifier
 *  @param two option strings for parser/classifier
 *  @param header is the header file for the job */
class WekaClassifierSparkMapper (classIndex:Int,metaLearner:String,classifierToTrain:String,classifierOptions:Array[String],
                                  rowparserOptions:Array[String],header:Instances) extends java.io.Serializable{

    //Initialize the parser and the Base Map task(It processes a set of instances and produces a classifier)
    var m_task=new WekaClassifierMapTask()
    var m_rowparser=new CSVToARFFHeaderMapTask()
    m_task.setOptions(classifierOptions)
    m_rowparser.setOptions(rowparserOptions)
   
    //Set the classifier to train 
    val obj=Class.forName(classifierToTrain).newInstance()
    val cla=obj.asInstanceOf[Classifier]
  
  //Check if a custom MetaLearner is requested
    if(metaLearner!="default"){
	    val obj2=Class.forName(metaLearner).newInstance()
	    val claMeta=obj2.asInstanceOf[SingleClassifierEnhancer]
	    claMeta.setClassifier(cla)
        m_task.setClassifier(claMeta)
    }
    else{
        m_task.setClassifier(cla) 
    }
     
  
    //Remove the summary from the headers. Set the class attribute
    val strippedHeader:Instances=CSVToARFFHeaderReduceTask.stripSummaryAtts(header)
    strippedHeader.setClassIndex(classIndex)
    m_rowparser.initParserOnly(CSVToARFFHeaderMapTask.instanceHeaderToAttributeNameList(strippedHeader))
    m_task.setup(strippedHeader)
  
  
  
  //true in make instance means classifier is updateable
  /**Map task for training classifiers
   * 
   * @param rows is a dataset partition
   * @return a trained classifier on the provided parition
   */
   def map(rows:Array[String]): Classifier={
     for(x <- rows){
       m_task.processInstance(m_rowparser.makeInstance(strippedHeader, true, m_rowparser.parseRowOnly(x)))
       }                                    //ToDo:many options here: updatable/not, batch/not, forced
       m_task.finalizeTask()
    return m_task.getClassifier()        //he also saves number of instances (for voting) in the same file. must check reducer
   } 

}