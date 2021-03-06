/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 *    WekaClassifierFoldBasedEvaluationSparkMapper.scala
 *    Copyright (C) 2014 School of Computer Science, University of Manchester
 *
 */

package uk.ac.manchester.ariskk.distributedWekaSpark.classifiers

import weka.distributed.WekaClassifierEvaluationMapTask
import weka.distributed.CSVToARFFHeaderReduceTask
import weka.distributed.CSVToARFFHeaderMapTask
import weka.core.Instances
import java.util.ArrayList
import weka.classifiers.Classifier
import weka.classifiers.evaluation.Evaluation
import weka.distributed.WekaClassifierEvaluationReduceTask
import weka.core.Instance


/**Mapper task for fold-based evaluation
 * 
 * 
 * Similar with the simple evaluation. Works only for classifiers.
 * @author Aris-Kyriakos Koliopoulos (ak.koliopoulos {[at]} gmail {[dot]} com)
 */
class WekaClassifierFoldBasedEvaluationSparkMapper(headers:Instances,classifier:Classifier,folds:Int,classIndex:Int) extends java.io.Serializable {

   //ToDo: isupdatable, forced trained
  
   
   var m_combiner=new WekaClassifierEvaluationReduceTask
   
   //Initialize a container for the WekaBase tasks
   var m_tasks=new ArrayList[WekaClassifierEvaluationMapTask]
   
   //Initialize a csv row parser and remove statistics from the headers and set the class attribute
   var m_rowparser=new CSVToARFFHeaderMapTask()
   var strippedHeaders=CSVToARFFHeaderReduceTask.stripSummaryAtts(headers)
   strippedHeaders.setClassIndex(classIndex) //ToDo:must be provided in the constructor
   m_rowparser.initParserOnly(CSVToARFFHeaderMapTask.instanceHeaderToAttributeNameList(strippedHeaders))
   val classAtt=strippedHeaders.classAttribute()
   
   //Initialize one WekaBase Map task for each fold
   val seed=1L
   val classAttSummaryName = CSVToARFFHeaderMapTask.ARFF_SUMMARY_ATTRIBUTE_PREFIX + classAtt.name()
   val summaryClassAtt=headers.attribute(classAttSummaryName)
   for(i<-0 to folds-1){
   m_tasks.add(new WekaClassifierEvaluationMapTask)
   m_tasks.get(i).setClassifier(classifier)
   m_tasks.get(i).setFoldNumber(i+1)
   m_tasks.get(i).setTotalNumFolds(folds)
   //Compute priors for the evaluation tasks
   m_tasks.get(i).setup(strippedHeaders, computePriors(), computePriorsCount(), seed, 0) //last is predFrac and is used to compute AUC/AuPRC
   }
  
   
  /**Fold-based Evaluation Mapper. Accepts the dataset in Array[String] format. Each String represents a row of the csv file
   * 
   * @param rows represent the dataset partition in Array[String] 
   * @return Evaluation is the fold-based evaluation model computed per partition
   */
  def map(rows:Array[String]): Evaluation={
   val evals=new ArrayList[Evaluation]
   for(i<-0 to rows.length-1){
     for(j<-0 to folds-1){
      //m_task checks if instance is in the fold set. no need to check here
      m_tasks.get(j).processInstance(m_rowparser.makeInstance(strippedHeaders, true, m_rowparser.parseRowOnly(rows(i))))
      }
    }
    for(j<-0 to folds-1){
      m_tasks.get(j).finalizeTask()
      evals.add(m_tasks.get(j).getEvaluation())
    }
    return m_combiner.aggregate(evals) //++
  }
   
   /**Fold-based Evaluation Mapper. Accepts the dataset in Array[Instance] format
   * 
   * @param rows represent the dataset partition in Array[Instance]
   * @return Evaluation is the fold-based evaluation model computed per partition
   */
  def map(rows:Array[Instance]): Evaluation={
   val evals=new ArrayList[Evaluation]
   for(i<-0 to rows.length-1){
     for(j<-0 to folds-1){
      //m_task checks if instance is in the fold set. no need to check here
       m_tasks.get(j).processInstance(rows(i))
      }
    }
    for(j<-0 to folds-1){
      m_tasks.get(j).finalizeTask()
      evals.add(m_tasks.get(j).getEvaluation())
    }
    return m_combiner.aggregate(evals)   //++
  }
  
  /**Fold-based Evaluation Mapper. Accepts the dataset as an Instances object
   * 
   * @param rows represent the dataset partition
   * @return Evaluation is the fold-based evaluation model computed per partition
   */
  def map(instances:Instances): Evaluation={
   val evals=new ArrayList[Evaluation]
   
     for(j<-0 to folds-1){
      //m_task checks if instance is in the fold set. no need to check here
     // m_tasks.get(j).setInstances(instances)  NOT YET IMPLEMENTED IN THE BASE TASKS.
      }
    
    for(j<-0 to folds-1){
      m_tasks.get(j).finalizeTask()
      evals.add(m_tasks.get(j).getEvaluation())
    }
    return m_combiner.aggregate(evals)   //++
  }
  
  
  
  
  
  
  
  
  /**Compute the attribute Priors for nominal and non nominal values
   * 
   * @return an array of Doubles (one per attribute)
   */
  def computePriors (): Array[Double]={ 
      if(classAtt.isNominal()){
        val priorsNom=new Array[Double](classAtt.numValues())
         for (i <- 0  to classAtt.numValues()-1) {
            val label = classAtt.value(i);
            val labelWithCount = summaryClassAtt.value(i).replace(label + "_", "").trim();
            priorsNom(i) = labelWithCount.toDouble }
            return priorsNom
           }
       else{
         val priorsNonNom=new Array[Double](1)
         priorsNonNom(0)=CSVToARFFHeaderMapTask.ArffSummaryNumericMetric.SUM.valueFromAttribute(summaryClassAtt)
         return priorsNonNom
      }
    
  }
    
  /**Computes prior counts (number of differet values)
   * 
   * @return a prior count
   */
  def computePriorsCount():Double={
      if(classAtt.isNominal()){return classAtt.numValues()}
      else{ return CSVToARFFHeaderMapTask.ArffSummaryNumericMetric.COUNT.valueFromAttribute(summaryClassAtt)}
      }
    
}