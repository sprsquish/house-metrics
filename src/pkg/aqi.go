package housemetrics

import "math"

type cRange struct {
	low float64
	hi  float64
}

type aqi struct {
	bpLow float64
	bpHi  float64
	iLow  float64
	iHi   float64
}

var maxAQI = aqi{225.5, 325.4, 300, 500}

var table = map[cRange]aqi{
	{0.0, 9.1}:     {0.0, 9.0, 0, 50},
	{9.1, 35.5}:    {9.0, 35.4, 50, 100},
	{35.5, 55.5}:   {35.5, 55.4, 100, 150},
	{55.5, 125.5}:  {55.5, 125.4, 150, 200},
	{125.5, 225.5}: {125.5, 225.4, 200, 300},
	{225.5, 325.5}: maxAQI,
}

func (a aqi) run(c float64) int {
	ret := (a.iHi-a.iLow)/(a.bpHi-a.bpLow)*(c-a.bpLow) + a.iLow
	return int(math.Round(ret))
}

func PM25ToAQI(c float64) int {
	calc := maxAQI

	for pm, aqi := range table {
		if pm.low <= c && c < pm.hi {
			calc = aqi
			break
		}
	}

	return calc.run(c)
}
