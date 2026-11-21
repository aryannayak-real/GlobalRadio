package com.aryannayak.globalradio.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aryannayak.globalradio.StationCard
import com.aryannayak.globalradio.data.RadioCountry
import com.aryannayak.globalradio.data.RadioStation

private val Genres = listOf(
    "Pop",
    "Rock",
    "Jazz",
    "Classical",
    "Electronic",
    "News",
    "Sports",
    "Talk"
)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    stations: List<RadioStation>,
    recentStations: List<RadioStation>,
    countries: List<RadioCountry>,
    favoriteIds: Set<String>,
    loading: Boolean,
    error: String,
    onPlay: (RadioStation) -> Unit,
    onFavorite: (RadioStation) -> Unit,
    onCountry: (RadioCountry) -> Unit,
    onGenre: (String) -> Unit,
    onSeeAll: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 12.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "CONTINUE LISTENING",
                        color = colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    Text(
                        "FROM YOUR HISTORY",
                        color = colors.onSurfaceVariant,
                        fontSize = 9.sp
                    )
                }

                if (recentStations.isEmpty()) {
                    Text(
                        "Stations you play will appear here.",
                        color = colors.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            recentStations.take(8),
                            key = { it.id.ifBlank { it.streamUrl } }
                        ) { station ->
                            Card(
                                onClick = { onPlay(station) },
                                modifier = Modifier.width(230.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = colors.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (station.favicon.startsWith("http")) {
                                        AsyncImage(
                                            model = station.favicon,
                                            contentDescription = station.name,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Radio,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = colors.primary
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            station.name,
                                            maxLines = 1,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )

                                        Text(
                                            station.country.ifBlank {
                                                "Worldwide"
                                            },
                                            maxLines = 1,
                                            color = colors.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "POPULAR WORLDWIDE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    TextButton(onClick = onSeeAll) {
                        Text("SEE ALL")
                    }
                }

                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = colors.error,
                        fontSize = 13.sp
                    )
                }

                if (!loading && stations.isEmpty() && error.isBlank()) {
                    Text(
                        "No stations available right now.",
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }

        items(
            stations.take(20),
            key = { it.id.ifBlank { it.streamUrl } }
        ) { station ->
            StationCard(
                station = station,
                favorite = station.id in favoriteIds,
                onPlay = { onPlay(station) },
                onFavorite = { onFavorite(station) }
            )
        }

        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "LISTEN BY COUNTRY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                if (countries.isEmpty()) {
                    Text(
                        "Country directory is loading or unavailable.",
                        color = colors.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            countries.take(25),
                            key = { it.code }
                        ) { country ->
                            FilterChip(
                                selected = false,
                                onClick = { onCountry(country) },
                                label = {
                                    Text(
                                        "${country.name} · ${country.stationCount}"
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "BROWSE BY GENRE",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(Genres) { genre ->
                        FilterChip(
                            selected = false,
                            onClick = { onGenre(genre) },
                            label = { Text(genre) }
                        )
                    }
                }
            }
        }
    }
}
