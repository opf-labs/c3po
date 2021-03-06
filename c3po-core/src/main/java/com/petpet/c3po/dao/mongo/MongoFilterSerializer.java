/*******************************************************************************
 * Copyright 2013 Petar Petrov <me@petarpetrov.org>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.petpet.c3po.dao.mongo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.petpet.c3po.api.model.helper.BetweenFilterCondition;
import com.petpet.c3po.api.model.helper.BetweenFilterCondition.Operator;
import com.petpet.c3po.api.model.helper.Filter;
import com.petpet.c3po.api.model.helper.FilterCondition;

/**
 * The {@link MongoFilterSerializer} translates a filter object to a
 * {@link DBObject} query, so that the dataset is first filtered and then the
 * persistence layer function is applied.
 * 
 * @author Petar Petrov <me@petarpetrov.org>
 * 
 */
public class MongoFilterSerializer {

  /**
   * A list of properties to exclude from wrapping. This is mongo specific.
   */
  private static final String[] EXCLUDE = { "_id", "uid", "collection", "name" };

  /**
   * A static exists query for Mongo.
   */
  private static final BasicDBObject EXISTS = new BasicDBObject( "$exists", true );

  /**
   * Serializes the given filter according to the strategy proposed here:
   * {@link Filter}. If the filter is null, then an empty {@link DBObject} is
   * returned.
   * 
   * @param filter
   *          the filter to serialize.
   * @return the Mongo {@link DBObject}
   */
  public DBObject serialize( Filter filter ) {
    DBObject result = new BasicDBObject();

    if ( filter != null ) {
      List<FilterCondition> conditions = filter.getConditions();
      Map<String, Integer> distinctFields = this.getDistinctFields( conditions );
      List<BasicDBObject> and = new ArrayList<BasicDBObject>();

      for ( String field : distinctFields.keySet() ) {

        if ( distinctFields.get( field ) == 1 ) {

          BasicDBObject val = this
              .getValueForField( field, conditions.toArray( new FilterCondition[conditions.size()] ) );
          and.add( val );

        } else {

          BasicDBObject orQuery = this.getOrQuery( conditions, field );
          and.add( orQuery );

        }

      }

      if ( and.size() > 0 ) {
        result.put( "$and", and );
      }

    }

    return result;
  }

  /**
   * Wraps the field within a metadata.[field].value if necessary, so that it
   * corresponds to the current element structure.
   * 
   * @param f
   *          the field to wrap
   * @return the wrapped field.
   */
  public String mapFieldToProperty( String f, Object value ) {
    if ( Arrays.asList( EXCLUDE ).contains( f ) ) {
      return f;
    }

    String result = "metadata." + f;
    return (value.equals( EXISTS )) ? result : result + ".value";
  }

  /**
   * Gets a {@link DBObject} that represents an or condition with all the values
   * of the given field.
   * 
   * @param conditions
   *          the filter conditions to look at.
   * @param field
   *          the field that has to be or concatenated.
   * @return the or condition.
   */
  private BasicDBObject getOrQuery( List<FilterCondition> conditions, String field ) {
    List<BasicDBObject> or = new ArrayList<BasicDBObject>();

    for ( FilterCondition fc : conditions ) {
      if ( field.equals( fc.getField() ) ) {
        BasicDBObject val = this.getValueForField( field, fc );
        or.add( val );
      }
    }

    return new BasicDBObject( "$or", or );
  }

  /**
   * Gets the distinct fields and the number of values for these fields within
   * the passed conditions.
   * 
   * @param conditions
   *          the conditions to look at.
   * @return a map of the distinct fields and the number of occurrences.
   */
  private Map<String, Integer> getDistinctFields( List<FilterCondition> conditions ) {
    Map<String, Integer> distinctFields = new HashMap<String, Integer>();

    for ( FilterCondition fc : conditions ) {
      Integer integer = distinctFields.get( fc.getField() );
      int res = (integer == null) ? 0 : integer;
      distinctFields.put( fc.getField(), ++res );
    }

    return distinctFields;
  }

  /**
   * Gets the first value for a given field.
   * 
   * @param conditions
   *          the conditions to look at.
   * @param field
   *          the field to look at.
   * @return the value or null.
   */
  private BasicDBObject getValueForField( String field, FilterCondition... conditions ) {
    for ( FilterCondition fc : conditions ) {
      if ( fc.getField().equals( field ) ) {

        Object val = fc.getValue();
        BasicDBObject res = null;
        if ( fc instanceof BetweenFilterCondition ) {
          BetweenFilterCondition bfc = (BetweenFilterCondition) fc;
          String mappedField = this.mapFieldToProperty( field, new Object() );
          BasicDBObject low = new BasicDBObject( mappedField, getBoundQuery( bfc.getLOperator(), bfc.getLValue() ) );
          BasicDBObject high = new BasicDBObject( mappedField, getBoundQuery( bfc.getHOperator(), bfc.getHValue() ) );

          List<BasicDBObject> and = new ArrayList<BasicDBObject>();
          and.add( low );
          and.add( high );
          res = new BasicDBObject( "$and", and );

        } else {
          val = (val == null) ? EXISTS : val;
          res = new BasicDBObject( this.mapFieldToProperty( field, val ), val );
        }

        return res;
      }

    }

    return null;
  }

  /**
   * Retrieves a bound query with the given operator and value.
   * 
   * @param op
   *          the operator to use.
   * @param val
   *          the value to use.
   * @return the mongo query.
   */
  private BasicDBObject getBoundQuery( Operator op, Object val ) {
    String operator = "";
    switch ( op ) {
      case GT:
        operator = "$gt";
        break;
      case GTE:
        operator = "$gte";
        break;
      case LT:
        operator = "$lt";
        break;
      case LTE:
        operator = "$lte";
        break;
    }

    return new BasicDBObject( operator, val );
  }
}
