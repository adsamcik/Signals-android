/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 3.0.12
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */

package com.adsamcik.signalcollector.map;

public class heatmap_stamp_t {
  private transient long swigCPtr;
  protected transient boolean swigCMemOwn;

  protected heatmap_stamp_t(long cPtr, boolean cMemoryOwn) {
    swigCMemOwn = cMemoryOwn;
    swigCPtr = cPtr;
  }

  protected static long getCPtr(heatmap_stamp_t obj) {
    return (obj == null) ? 0 : obj.swigCPtr;
  }

  protected void finalize() {
    delete();
  }

  public synchronized void delete() {
    if (swigCPtr != 0) {
      if (swigCMemOwn) {
        swigCMemOwn = false;
        exampleJNI.delete_heatmap_stamp_t(swigCPtr);
      }
      swigCPtr = 0;
    }
  }

  public void setBuf(SWIGTYPE_p_float value) {
    exampleJNI.heatmap_stamp_t_buf_set(swigCPtr, this, SWIGTYPE_p_float.getCPtr(value));
  }

  public SWIGTYPE_p_float getBuf() {
    long cPtr = exampleJNI.heatmap_stamp_t_buf_get(swigCPtr, this);
    return (cPtr == 0) ? null : new SWIGTYPE_p_float(cPtr, false);
  }

  public void setW(long value) {
    exampleJNI.heatmap_stamp_t_w_set(swigCPtr, this, value);
  }

  public long getW() {
    return exampleJNI.heatmap_stamp_t_w_get(swigCPtr, this);
  }

  public void setH(long value) {
    exampleJNI.heatmap_stamp_t_h_set(swigCPtr, this, value);
  }

  public long getH() {
    return exampleJNI.heatmap_stamp_t_h_get(swigCPtr, this);
  }

  public heatmap_stamp_t() {
    this(exampleJNI.new_heatmap_stamp_t(), true);
  }

}
