package dev.luma.launcher

import org.junit.Assert.*
import org.junit.Test

class GridNavigationTest {
    @Test fun horizontalMovementDoesNotWrapRows() {
        assertEquals(5,GridNavigation.move(5,18,6,1,0))
        assertEquals(6,GridNavigation.move(6,18,6,-1,0))
    }
    @Test fun downClampsToShortLastRow() { assertEquals(13,GridNavigation.move(11,14,6,0,1)) }
    @Test fun downAtBottomStaysPut() { assertEquals(13,GridNavigation.move(13,14,6,0,1)) }
    @Test fun upFromFirstRowTargetsToolbar() { assertEquals(-1,GridNavigation.move(3,14,6,0,-1)) }
    @Test fun verticalMovementKeepsColumn() { assertEquals(9,GridNavigation.move(3,18,6,0,1));assertEquals(3,GridNavigation.move(9,18,6,0,-1)) }
    @Test fun emptyGridHasNoFocus() { assertEquals(-1,GridNavigation.move(0,0,6,1,0)) }
    @Test fun staleAndDuplicatePreferencesAreReconciled() {
        assertEquals(listOf("b","c","a"),GridNavigation.ordered(listOf("a","b","c"),listOf("missing","c","c"),listOf("b","b","old"),emptySet()))
    }
    @Test fun hiddenFavouriteCannotLeakOntoHome() {
        assertEquals(listOf("a","c"),GridNavigation.ordered(listOf("a","b","c"),listOf("b","a"),listOf("b"),setOf("b")))
    }
    @Test fun repeatedMovesAlwaysStayInBounds() {
        var focus=0
        repeat(1000) { focus=GridNavigation.move(focus,17,6,if(it%3==0)-1 else 1,if(it%4==0)1 else 0);assertTrue(focus in 0..16) }
    }
}
