package cc.makin.coinmonitor

import cc.makin.coinmonitor.idofcow.IdOfCowStats

data class Banner(
    val name: String,
    val coins: List<CoinResult>,
    val idOfCowStats: IdOfCowStats? = null,
)
