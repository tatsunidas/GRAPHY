#!/bin/sh
# -------------------------------------------------------------------------
# copy needed JBOSS components into DCM4CHEE installation
# -------------------------------------------------------------------------

DIRNAME=`dirname $0`
DCM4CHEE_HOME="$DIRNAME"/..
DCM4CHEE_SERV="$DCM4CHEE_HOME"/server/default

if [ x$1 = x ]; then
  echo "Usage: $0 <path-to-jboss-4.2.3.GA-installation-directory>"
  exit 1
fi

JBOSS_HOME="$1"
JBOSS_SERV="$JBOSS_HOME"/server/default

if [ ! -f "$JBOSS_HOME"/bin/run.jar ]; then
  echo Could not locate jboss-4.2.3.GA in "$JBOSS_HOME"
  exit 1
fi

cp -v "$JBOSS_HOME"/bin/run.jar \
  "$JBOSS_HOME"/bin/shutdown.bat \
  "$JBOSS_HOME"/bin/shutdown.jar \
  "$JBOSS_HOME"/bin/shutdown.sh \
  "$JBOSS_HOME"/bin/twiddle.bat \
  "$JBOSS_HOME"/bin/twiddle.jar \
  "$JBOSS_HOME"/bin/twiddle.sh \
  "$DCM4CHEE_HOME"/bin
  
mkdir "$DCM4CHEE_HOME"/client
cp -v "$JBOSS_HOME"/client/jbossall-client.jar \
  "$JBOSS_HOME"/client/getopt.jar \
  "$DCM4CHEE_HOME"/client

cp -v -R "$JBOSS_HOME"/lib "$DCM4CHEE_HOME"

cp -v "$JBOSS_SERV"/conf/jbossjta-properties.xml \
  "$JBOSS_SERV"/conf/jndi.properties \
  "$JBOSS_SERV"/conf/standardjbosscmp-jdbc.xml \
  "$DCM4CHEE_SERV"/conf
cp -v -R "$JBOSS_SERV"/conf/props \
  "$JBOSS_SERV"/conf/xmdesc \
  "$DCM4CHEE_SERV"/conf

cp -v "$JBOSS_SERV"/lib/* "$DCM4CHEE_SERV/lib"
rm -v "$DCM4CHEE_SERV"/lib/jbossmq.jar
 
cp -v "$JBOSS_SERV"/deploy/hsqldb-ds.xml \
  "$JBOSS_SERV"/deploy/jboss-ha-local-jdbc.rar \
  "$JBOSS_SERV"/deploy/jbossjca-service.xml \
  "$JBOSS_SERV"/deploy/jboss-local-jdbc.rar \
  "$JBOSS_SERV"/deploy/jmx-invoker-service.xml \
  "$JBOSS_SERV"/deploy/jsr88-service.xml \
  "$JBOSS_SERV"/deploy/monitoring-service.xml \
  "$JBOSS_SERV"/deploy/jms/jms-ra.rar \
  "$DCM4CHEE_SERV"/deploy

cp -v -R "$JBOSS_SERV"/deploy/http-invoker.sar \
  "$JBOSS_SERV"/deploy/jboss-aop-jdk50.deployer \
  "$JBOSS_SERV"/deploy/jboss-bean.deployer \
  "$JBOSS_SERV"/deploy/jboss-web.deployer \
  "$JBOSS_SERV"/deploy/management \
  "$DCM4CHEE_SERV"/deploy

cp -v "$JBOSS_SERV"/deploy/jmx-console.war/checkJNDI.jsp \
  "$JBOSS_SERV"/deploy/jmx-console.war/displayMBeans.jsp \
  "$JBOSS_SERV"/deploy/jmx-console.war/displayOpResult.jsp \
  "$JBOSS_SERV"/deploy/jmx-console.war/index.jsp \
  "$JBOSS_SERV"/deploy/jmx-console.war/jboss.css \
  "$JBOSS_SERV"/deploy/jmx-console.war/style_master.css \
  "$DCM4CHEE_SERV"/deploy/jmx-console.war
  
cp -v -R "$JBOSS_SERV"/deploy/jmx-console.war/cluster \
  "$JBOSS_SERV"/deploy/jmx-console.war/images \
  "$JBOSS_SERV"/deploy/jmx-console.war/META-INF \
  "$JBOSS_SERV"/deploy/jmx-console.war/WEB-INF \
  "$DCM4CHEE_SERV"/deploy/jmx-console.war
  