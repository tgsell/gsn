/**
* Global Sensor Networks (GSN) Source Code
* Copyright (c) 2006-2014, Ecole Polytechnique Federale de Lausanne (EPFL)
* 
* This file is part of GSN.
* 
* GSN is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
* 
* GSN is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with GSN.  If not, see <http://www.gnu.org/licenses/>.
* 
* File: webservices/standard/src/gsn/webservice/standard/xsd/ExtensionMapper.java
*
* @author Timotee Maret
*
*/


/**
 * ExtensionMapper.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis2 version: 1.4.1  Built on : Aug 13, 2008 (05:03:41 LKT)
 */

            package gsn.webservice.standard.xsd;
            /**
            *  ExtensionMapper class
            */
        
        public  class ExtensionMapper{

          public static java.lang.Object getTypeObject(java.lang.String namespaceURI,
                                                       java.lang.String typeName,
                                                       javax.xml.stream.XMLStreamReader reader) throws java.lang.Exception{

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_ConfProcessor".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_ConfProcessor.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_ConfInfo".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_ConfInfo.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_ACDetails".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_ACDetails.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_WrapperURL".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_WrapperURL.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://datarequest.http.gsn/xsd".equals(namespaceURI) &&
                  "AggregationCriterion".equals(typeName)){
                   
                            return  gsn.http.datarequest.xsd.AggregationCriterion.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_ConfOutputStructure".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_ConfOutputStructure.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_ContainerDetails".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_ContainerDetails.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://datarequest.http.gsn/xsd".equals(namespaceURI) &&
                  "StandardCriterion".equals(typeName)){
                   
                            return  gsn.http.datarequest.xsd.StandardCriterion.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_FieldSelector".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_FieldSelector.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_VirtualSensorDetails".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_VirtualSensorDetails.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_ConfAddressing".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_ConfAddressing.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_DataField".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_DataField.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_QueryResult".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_QueryResult.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_DetailsType".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_DetailsType.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_ConfPredicate".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_ConfPredicate.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_WrapperDetails".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_WrapperDetails.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_ConfWrapper".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_ConfWrapper.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://standard.webservice.gsn/xsd".equals(namespaceURI) &&
                  "GSNWebService_StreamElement".equals(typeName)){
                   
                            return  gsn.webservice.standard.xsd.GSNWebService_StreamElement.Factory.parse(reader);
                        

                  }

              
                  if (
                  "http://datarequest.http.gsn/xsd".equals(namespaceURI) &&
                  "AbstractCriterion".equals(typeName)){
                   
                            return  gsn.http.datarequest.xsd.AbstractCriterion.Factory.parse(reader);
                        

                  }

              
             throw new org.apache.axis2.databinding.ADBException("Unsupported type " + namespaceURI + " " + typeName);
          }

        }
    