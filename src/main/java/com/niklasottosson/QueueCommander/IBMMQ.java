package com.niklasottosson.QueueCommander;

import com.niklasottosson.QueueCommander.model.Queue;
import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.ibm.mq.constants.MQConstants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Created by malen on 2019-02-18.
 */
public class IBMMQ {
    private Hashtable<String,Object> mqht;
    private MQQueueManager qmgr;
    private PCFMessageAgent pcfMessageAgent;

    static private String QMANAGER = "QM1";

    public IBMMQ(){

    }

    public IBMMQ(Configuration conf){

        setConfig(conf);
    }

    public void setConfig(Configuration conf){
        mqht = new Hashtable<String, Object>(5);
        mqht.put(MQConstants.CHANNEL_PROPERTY, conf.getChannel());
        mqht.put(MQConstants.HOST_NAME_PROPERTY, conf.getHost());
        mqht.put(MQConstants.PORT_PROPERTY, conf.getPort());
        mqht.put(MQConstants.USER_ID_PROPERTY, conf.getUser());
        mqht.put(MQConstants.PASSWORD_PROPERTY, conf.getPassword());

        QMANAGER = conf.getQmanager();
    }

    public boolean connect() {
        // Check if configuration is set
        if(mqht == null){
            System.out.println("Configuration not set");
            return false;
        }
/*
        mqht = new Hashtable<String, Object>(5);
        mqht.put(MQConstants.CHANNEL_PROPERTY, "DEV.ADMIN.SVRCONN");
        //mqht.put(MQConstants.HOST_NAME_PROPERTY, "192.168.0.105");
        mqht.put(MQConstants.HOST_NAME_PROPERTY, "localhost");
        mqht.put(MQConstants.PORT_PROPERTY, 1414);
        mqht.put(MQConstants.USER_ID_PROPERTY, "admin");
        mqht.put(MQConstants.PASSWORD_PROPERTY, "passw0rd");
*/
        //mqht.put(MQConstants.CHANNEL_PROPERTY, "WEBMETHODS");
        //mqht.put(MQConstants.HOST_NAME_PROPERTY, "mq01-t.icc.it.gu.se");
        //mqht.put(MQConstants.PORT_PROPERTY, 11535);
        //mqht.put(MQConstants.USER_ID_PROPERTY, "xottni");
        //mqht.put(MQConstants.PASSWORD_PROPERTY, "cum7*RYQ");

        try {

            qmgr = new MQQueueManager(QMANAGER, mqht);
            //System.out.println("Successfully connected to qmgr: " + "QM1");

            pcfMessageAgent = new PCFMessageAgent(qmgr);
            //System.out.println("Successfully created a PCF agent");

        } catch (MQDataException e) {
            e.printStackTrace();
        } catch (MQException e) {
            e.printStackTrace();
        }

        return true;
    }

    public boolean disconnect(){
        try {
            if (pcfMessageAgent != null)  {
                pcfMessageAgent.disconnect();
                //System.out.println("Disconnected from agent");
            }
        }
        catch (MQDataException e) {
            System.out.println("CC=" +e.completionCode + " : RC=" + e.reasonCode);
        }

        try {
            if (qmgr != null) {
                qmgr.disconnect();
                //System.out.println("Disconnected from "+ "QM1");
            }
        }
        catch (MQException e) {
            System.out.println("CC=" +e.completionCode + " : RC=" + e.reasonCode);
        }

        return true;
    }

    // New function
    public List<Queue> getQueueList(){

        List<Queue> result = new ArrayList<Queue>(100);

        PCFMessage   request = null;
        PCFMessage[] responses = null;

        // https://www.ibm.com/support/knowledgecenter/en/SSFKSJ_9.1.0/com.ibm.mq.ref.adm.doc/q087880_.htm
        request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q_STATUS);

        /**
         * You can explicitly set a queue name like "TEST.Q1" or
         * use a wild card like "TEST.*"
         */
        request.addParameter(CMQC.MQCA_Q_NAME, "*");

        // Add a parameter to select TYPE of QUEUE (rather than HANDLE i.e. MQIACF_Q_HANDLE)
        request.addParameter(CMQCFC.MQIACF_Q_STATUS_TYPE, CMQCFC.MQIACF_Q_STATUS);

        // Add a parameter that selects all of the attributes we want
        request.addParameter(CMQCFC.MQIACF_Q_STATUS_ATTRS,
                new int [] { CMQC.MQCA_Q_NAME,
                        CMQC.MQIA_CURRENT_Q_DEPTH,
                        CMQC.MQIA_OPEN_INPUT_COUNT,
                        CMQC.MQIA_OPEN_OUTPUT_COUNT,
                        CMQCFC.MQCACF_LAST_PUT_DATE,
                        CMQCFC.MQCACF_LAST_PUT_TIME,
                        CMQCFC.MQCACF_LAST_GET_DATE,
                        CMQCFC.MQCACF_LAST_GET_TIME,
                });
        /**
         * Other attributes that can be used for TYPE(QUEUE)
         * - MQIA_MONITORING_Q
         * - MQCACF_MEDIA_LOG_EXTENT_NAME
         * - MQIACF_OLDEST_MSG_AGE
         * - MQIACF_Q_TIME_INDICATOR
         * - MQIACF_UNCOMMITTED_MSGS
         */

        try {
            responses = pcfMessageAgent.send(request);
        } catch (MQDataException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


        for (int i = 0; i < responses.length; i++){

            if ( ((responses[i]).getCompCode() == CMQC.MQCC_OK) &&
                    ((responses[i]).getParameterValue(CMQC.MQCA_Q_NAME) != null) ) {

                String name = null;
                try {
                    name = responses[i].getStringParameterValue(CMQC.MQCA_Q_NAME);
                    if (name != null)
                        name = name.trim();

                    int depth = responses[i].getIntParameterValue(CMQC.MQIA_CURRENT_Q_DEPTH);
                    int iprocs = responses[i].getIntParameterValue(CMQC.MQIA_OPEN_INPUT_COUNT);
                    int oprocs = responses[i].getIntParameterValue(CMQC.MQIA_OPEN_OUTPUT_COUNT);

                    String lastPutDate = responses[i].getStringParameterValue(CMQCFC.MQCACF_LAST_PUT_DATE);
                    if (lastPutDate != null)
                        lastPutDate = lastPutDate.trim();

                    String lastPutTime = responses[i].getStringParameterValue(CMQCFC.MQCACF_LAST_PUT_TIME);
                    if (lastPutTime != null)
                        lastPutTime = lastPutTime.trim();

                    String lastGetDate = responses[i].getStringParameterValue(CMQCFC.MQCACF_LAST_GET_DATE);
                    if (lastGetDate != null)
                        lastGetDate = lastGetDate.trim();

                    String lastGetTime = responses[i].getStringParameterValue(CMQCFC.MQCACF_LAST_GET_TIME);
                    if (lastGetTime != null)
                        lastGetTime = lastGetTime.trim();
                    //System.out.println("Name="+name + " : depth="+depth + " : iprocs="+iprocs+" : oprocs="+oprocs+" : lastPutDate='"+lastPutDate+"' : lastPutTime='"+lastPutTime+"' : lastGetDate='"+lastGetDate+"' : lastGetTime='"+lastGetTime+"'");

                    result.add(new Queue(name, depth, lastPutDate + " " + lastPutTime, lastGetDate + " " + lastGetTime));

                } catch (PCFException e) {
                    e.printStackTrace();
                }

            }
        }

    return result;

    }
}
