package com.nct32.notesplus.text

/**
 * A map of common English misspellings to their corrections.
 *
 * A curated list of the most frequent typos and misspellings in casual writing. Used by the
 * [AutocorrectEngine] as a first pass before falling back to dictionary-based edit-distance
 * suggestions. Keys are lowercase; lookups are case-insensitive.
 */
object CommonMisspellings {

    /** Misspelling (lowercase) -> correction. */
    val MAP: Map<String, String> = buildMap {
        put("recieve", "receive")
        put("definately", "definitely")
        put("seperate", "separate")
        put("occured", "occurred")
        put("untill", "until")
        put("wich", "which")
        put("thier", "their")
        put("becuase", "because")
        put("tommorow", "tomorrow")
        put("teh", "the")
        put("adn", "and")
        put("wiht", "with")
        put("fo", "for")
        put("taht", "that")
        put("throught", "through")
        put("goverment", "government")
        put("alott", "a lot")
        put("becaus", "because")
        put("beleive", "believe")
        put("calender", "calendar")
        put("comming", "coming")
        put("decison", "decision")
        put("embarass", "embarrass")
        put("enviroment", "environment")
        put("existance", "existence")
        put("familar", "familiar")
        put("foriegn", "foreign")
        put("freind", "friend")
        put("happend", "happened")
        put("immediatly", "immediately")
        put("independant", "independent")
        put("knowlege", "knowledge")
        put("langauge", "language")
        put("libary", "library")
        put("neccessary", "necessary")
        put("noticable", "noticeable")
        put("occassion", "occasion")
        put("persue", "pursue")
        put("posession", "possession")
        put("prefered", "preferred")
        put("propogate", "propagate")
        put("publically", "publicly")
        put("questionaire", "questionnaire")
        put("realy", "really")
        put("reccomend", "recommend")
        put("refered", "referred")
        put("relevent", "relevant")
        put("remeber", "remember")
        put("repitition", "repetition")
        put("succesful", "successful")
        put("suprise", "surprise")
        put("treshold", "threshold")
        put("toem", "thumb")
        put("tranfer", "transfer")
        put("truely", "truly")
        put("wether", "whether")
        put("writting", "writing")
    }
}
