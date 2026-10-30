package com.karelisio.ruban.ui.screens.people

import com.karelisio.ruban.domain.TextMatch

/** Common French name days (calendrier des fêtes), used to suggest a "fête" from a first name. */
object NameDays {
    private const val DATA = """
Basile 01-02|Geneviève 01-03|Odilon 01-04|Édouard 01-05|Raymond 01-07|Guillaume 01-10|Paulin 01-11|Yvette 01-13|Nina 01-14|
Rémi 01-15|Marcel 01-16|Roseline 01-17|Prisca 01-18|Marius 01-19|Sébastien 01-20|Agnès 01-21|Vincent 01-22|Paule 01-26|
Pauline 01-26|Angèle 01-27|Gildas 01-29|Martine 01-30|Marcelle 01-31|Ella 02-01|Blaise 02-03|Véronique 02-04|Agathe 02-05|
Gaston 02-06|Eugénie 02-07|Jacqueline 02-08|Apolline 02-09|Arnaud 02-10|Félix 02-12|Béatrice 02-13|Valentin 02-14|
Valentine 02-14|Claude 02-15|Julienne 02-16|Alexis 02-17|Bernadette 02-18|Gabin 02-19|Aimée 02-20|Isabelle 02-22|
Lazare 02-23|Roméo 02-25|Nestor 02-26|Honorine 02-27|Romain 02-28|Romane 02-28|Aubin 03-01|Casimir 03-04|Olive 03-05|
Colette 03-06|Félicité 03-07|Françoise 03-09|Vivien 03-10|Rosine 03-11|Justine 03-12|Rodrigue 03-13|Mathilde 03-14|
Louise 03-15|Bénédicte 03-16|Patrice 03-17|Patrick 03-17|Patricia 03-17|Cyrille 03-18|Cyril 03-18|Joseph 03-19|
Joséphine 03-19|Clémence 03-21|Léa 03-22|Victorien 03-23|Larissa 03-26|Gontran 03-28|Gwladys 03-29|Amédée 03-30|
Benjamin 03-31|Hugues 04-01|Hugo 04-01|Huguette 04-01|Sandrine 04-02|Richard 04-03|Isidore 04-04|Irène 04-05|
Marcellin 04-06|Julie 04-08|Gautier 04-09|Fulbert 04-10|Stanislas 04-11|Jules 04-12|Ida 04-13|Maxime 04-14|
Anicet 04-17|Emma 04-19|Odette 04-20|Anselme 04-21|Alexandre 04-22|Alexandra 04-22|Georges 04-23|Fidèle 04-24|
Marc 04-25|Alida 04-26|Zita 04-27|Valérie 04-28|Robert 04-30|Zoé 05-02|Boris 05-02|Philippe 05-03|Sylvain 05-04|
Florian 05-04|Judith 05-05|Prudence 05-06|Gisèle 05-07|Pacôme 05-09|Solange 05-10|Estelle 05-11|Achille 05-12|
Rolande 05-13|Matthias 05-14|Denise 05-15|Honoré 05-16|Pascal 05-17|Pascale 05-17|Éric 05-18|Yves 05-19|Yvonne 05-19|
Constantin 05-21|Émile 05-22|Rita 05-22|Didier 05-23|Donatien 05-24|Sophie 05-25|Bérenger 05-26|Germain 05-28|
Aymar 05-29|Ferdinand 05-30|Jeanne 05-30|Perrine 05-31|Justin 06-01|Blandine 06-02|Kévin 06-03|Clotilde 06-04|
Igor 06-05|Norbert 06-06|Gilbert 06-07|Médard 06-08|Diane 06-09|Barnabé 06-11|Guy 06-12|Antoine 06-13|Élisée 06-14|
Germaine 06-15|Hervé 06-17|Léonce 06-18|Romuald 06-19|Silvère 06-20|Rodolphe 06-21|Alban 06-22|Audrey 06-23|
Fernand 06-27|Irénée 06-28|Pierre 06-29|Paul 06-29|Martial 06-30|Thierry 07-01|Thomas 07-03|Tom 07-03|Florent 07-04|
Mariette 07-06|Raoul 07-07|Thibault 07-08|Thibaut 07-08|Amandine 07-09|Ulrich 07-10|Benoît 07-11|Olivier 07-12|
Henri 07-13|Joël 07-13|Camille 07-14|Donald 07-15|Charlotte 07-17|Frédéric 07-18|Arsène 07-19|Marina 07-20|Marine 07-20|
Victor 07-21|Madeleine 07-22|Brigitte 07-23|Christine 07-24|Jacques 07-25|Anne 07-26|Anna 07-26|Anaïs 07-26|
Joachim 07-26|Nathalie 07-27|Samson 07-28|Marthe 07-29|Juliette 07-30|Ignace 07-31|Alphonse 08-01|Julien 08-02|
Lydie 08-03|Abel 08-05|Gaétan 08-07|Dominique 08-08|Laurent 08-10|Laure 08-10|Claire 08-11|Clara 08-11|Suzanne 08-11|
Clarisse 08-12|Hippolyte 08-13|Évrard 08-14|Marie 08-15|Manon 08-15|Marion 08-15|Maryse 08-15|Armel 08-16|Hyacinthe 08-17|
Hélène 08-18|Léna 08-18|Jean-Eudes 08-19|Bernard 08-20|Christophe 08-21|Fabrice 08-22|Rose 08-23|Barthélemy 08-24|
Louis 08-25|Ludovic 08-25|Loïc 08-25|Natacha 08-26|Monique 08-27|Augustin 08-28|Sabine 08-29|Fiacre 08-30|
Aristide 08-31|Gilles 09-01|Ingrid 09-02|Grégoire 09-03|Rosalie 09-04|Raïssa 09-05|Bertrand 09-06|Reine 09-07|
Alain 09-09|Inès 09-10|Adelphe 09-11|Aimé 09-13|Roland 09-15|Édith 09-16|Renaud 09-17|Nadège 09-18|Émilie 09-19|
Davy 09-20|Matthieu 09-21|Mathieu 09-21|Maurice 09-22|Constant 09-23|Thècle 09-24|Hermann 09-25|Côme 09-26|Damien 09-26|
Venceslas 09-28|Michel 09-29|Michèle 09-29|Michelle 09-29|Mickaël 09-29|Gabriel 09-29|Gabrielle 09-29|Raphaël 09-29|
Raphaëlle 09-29|Jérôme 09-30|Thérèse 10-01|Léger 10-02|Gérard 10-03|François 10-04|Franck 10-04|Fleur 10-05|Bruno 10-06|
Serge 10-07|Pélagie 10-08|Denis 10-09|Ghislain 10-10|Firmin 10-11|Wilfried 10-12|Géraud 10-13|Juste 10-14|Edwige 10-16|
Baudouin 10-17|Luc 10-18|Lucas 10-18|René 10-19|Adeline 10-20|Céline 10-21|Élodie 10-22|Florentin 10-24|Doria 10-25|
Dimitri 10-26|Émeline 10-27|Simon 10-28|Simone 10-28|Jude 10-28|Narcisse 10-29|Bienvenue 10-30|Quentin 10-31|
Hubert 11-03|Charles 11-04|Sylvie 11-05|Bertille 11-06|Carine 11-07|Geoffroy 11-08|Théodore 11-09|Théo 11-09|Léon 11-10|
Léo 11-10|Martin 11-11|Christian 11-12|Brice 11-13|Sidoine 11-14|Albert 11-15|Arthur 11-15|Marguerite 11-16|Margaux 11-16|
Élisabeth 11-17|Élise 11-17|Babette 11-17|Aude 11-18|Tanguy 11-19|Edmond 11-20|Cécile 11-22|Clément 11-23|Flora 11-24|
Catherine 11-25|Delphine 11-26|Séverin 11-27|Saturnin 11-29|André 11-30|Andrée 11-30|Florence 12-01|Viviane 12-02|
Xavier 12-03|Barbara 12-04|Gérald 12-05|Nicolas 12-06|Nicole 12-06|Ambroise 12-07|Romaric 12-10|Daniel 12-11|
Danielle 12-11|Chantal 12-12|Lucie 12-13|Odile 12-14|Ninon 12-15|Alice 12-16|Alicia 12-16|Gaël 12-17|Gaëlle 12-17|
Gatien 12-18|Urbain 12-19|Théophile 12-20|Armand 12-23|Adèle 12-24|Adam 12-24|Ève 12-24|Noël 12-25|Noëlle 12-25|
Étienne 12-26|Stéphane 12-26|Stéphanie 12-26|Jean 12-27|Fabiola 12-27|David 12-29|Roger 12-30|Sylvestre 12-31
"""

    private val map: Map<String, Pair<Int, Int>> by lazy {
        DATA.replace("\n", "").split('|').mapNotNull { entry ->
            val parts = entry.trim().split(' ')
            if (parts.size != 2) return@mapNotNull null
            val (m, d) = parts[1].split('-').map(String::toInt)
            TextMatch.normalize(parts[0]) to (m to d)
        }.toMap()
    }

    /** (month, day) for the first word of [name], if known. */
    fun find(name: String): Pair<Int, Int>? {
        val first = name.trim().split(Regex("[\\s-]+")).firstOrNull()?.takeIf { it.length >= 2 } ?: return null
        return map[TextMatch.normalize(first)]
    }
}
