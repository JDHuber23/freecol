
package net.sf.freecol.common.model;



/**
* This interface marks the locations where a <code>Unit</code> can work.
*/
public interface WorkLocation extends Location {

    /**
    * Returns the production of the given type of goods.
    */
    public int getProductionOf(int goodsType);
}
