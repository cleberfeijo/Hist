package br.uff.hist.core.model

val RIO_DAS_OSTRAS = GeoPoint(-22.5269, -41.9450)
const val CHAT_JOIN_RADIUS_M = 200
const val USER_NEARBY_RADIUS_M = 1000
const val USER_REFETCH_DISTANCE_M = 25.0
const val MAX_NEARBY_RADIUS_M = 50_000
const val NEARBY_LIMIT = 200
const val BACKGROUND_TOLERANCE_MS = 5 * 60_000L
const val PING_INTERVAL_MS = 30_000L
const val LOCATION_MIN_DISPLACEMENT_M = 10.0

// Limites de validação do servidor, compartilhados pelos ViewModels.
const val PASSWORD_MIN_LENGTH = 8
const val PASSWORD_MAX_LENGTH = 72
const val REASON_MIN_LENGTH = 3
const val REASON_MAX_LENGTH = 500
const val LANDMARK_NAME_MIN_LENGTH = 2
const val LANDMARK_NAME_MAX_LENGTH = 200
const val DESCRIPTION_MAX_LENGTH = 5000
const val CURIOSITY_MIN_LENGTH = 10
const val CURIOSITY_MAX_LENGTH = 2000
