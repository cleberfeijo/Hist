package br.uff.hist.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTest {
    @Test fun fromIdKnown() = assertEquals(Category.IGREJA, Category.fromId("igreja"))
    @Test fun fromIdIgnoresCase() = assertEquals(Category.IGREJA, Category.fromId("IGREJA"))
    @Test fun fromIdUnknownIsOutro() = assertEquals(Category.OUTRO, Category.fromId("capela"))
    @Test fun fromIdNullIsOutro() = assertEquals(Category.OUTRO, Category.fromId(null))
    @Test fun fromIdEmptyIsOutro() = assertEquals(Category.OUTRO, Category.fromId(""))
    @Test fun hasNineCategories() = assertEquals(9, Category.entries.size)
}
