/*
 * Copyright (c) 2007-2010 Concurrent, Inc. All Rights Reserved.
 *
 * Project and contact information: http://www.cascading.org/
 *
 * This file is part of the Cascading project.
 *
 * Cascading is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Cascading is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Cascading.  If not, see <http://www.gnu.org/licenses/>.
 */

package cascading.tuple.hadoop;

import java.io.IOException;
import java.util.Comparator;

import cascading.CascadingException;
import cascading.tuple.Fields;
import cascading.tuple.StreamComparator;
import cascading.tuple.Tuple;
import cascading.tuple.TupleInputStream;
import cascading.util.Util;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.io.RawComparator;

/** Class DeserializerComparator is the base class for all Cascading comparator classes. */
public abstract class DeserializerComparator<T> extends Configured implements RawComparator<T>
  {
  BufferedInputStream lhsBuffer = new BufferedInputStream();
  BufferedInputStream rhsBuffer = new BufferedInputStream();

  TupleSerialization tupleSerialization;

  TupleInputStream lhsStream;
  TupleInputStream rhsStream;

  Comparator[] groupComparators;

  @Override
  public void setConf( Configuration conf )
    {
    if( conf == null )
      return;

    super.setConf( conf );

    tupleSerialization = new TupleSerialization( conf );

    // get new readers so deserializers don't compete for the buffer
    lhsStream = new TupleInputStream( lhsBuffer, tupleSerialization.getElementReader() );
    rhsStream = new TupleInputStream( rhsBuffer, tupleSerialization.getElementReader() );

    groupComparators = deserializeComparatorsFor( "cascading.group.comparator" );
    groupComparators = delegatingComparatorsFor( groupComparators );
    }

  Comparator[] deserializeComparatorsFor( String name )
    {
    try
      {
      if( getConf() == null )
        throw new IllegalStateException( "no conf set" );

      String value = getConf().get( name );

      if( value == null )
        return new Comparator[getConf().getInt( name + ".size", 1 )];

      Fields fields = (Fields) Util.deserializeBase64( value );

      return fields.getComparators();
      }
    catch( IOException exception )
      {
      throw new CascadingException( "unable to deserialize comparators for: " + name );
      }
    }

  Comparator[] delegatingComparatorsFor( Comparator[] fieldComparators )
    {
    Comparator[] comparators = new Comparator[fieldComparators.length];

    for( int i = 0; i < comparators.length; i++ )
      {
      if( fieldComparators[ i ] instanceof StreamComparator )
        comparators[ i ] = new TupleElementStreamComparator( (StreamComparator) fieldComparators[ i ] );
      else if( fieldComparators[ i ] != null )
        comparators[ i ] = new TupleElementComparator( fieldComparators[ i ] );
      else
        comparators[ i ] = new DelegatingTupleElementComparator( tupleSerialization );
      }

    return comparators;
    }

  final int compareTuples( Comparator[] comparators, Tuple lhs, Tuple rhs )
    {
    int lhsLen = lhs.size();
    int rhsLen = rhs.size();

    int c = lhsLen - rhsLen;

    if( c != 0 )
      return c;

    for( int i = 0; i < lhsLen; i++ )
      {
      // hack to support comparators array length of 1
      c = comparators[ i % comparators.length ].compare( lhs.getObject( i ), rhs.getObject( i ) );

      if( c != 0 )
        return c;
      }

    return 0;
    }

  final int compareTuples( Comparator[] comparators ) throws IOException
    {
    int lhsLen = lhsStream.getNumElements();
    int rhsLen = rhsStream.getNumElements();

    int c = lhsLen - rhsLen;

    if( c != 0 )
      return c;

    for( int i = 0; i < lhsLen; i++ )
      {
      // hack to support comparators array length of 1
      c = ( (StreamComparator) comparators[ i % comparators.length ] ).compare( lhsStream, rhsStream );

      if( c != 0 )
        return c;
      }

    return 0;
    }
  }