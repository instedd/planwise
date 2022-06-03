#!/usr/bin/env node

const fs = require('fs'),
      request = require('request'),
      AdmZip = require('adm-zip'),
      xmldom = new (require('xmldom').DOMParser)(),
      tj = require('togeojson'),
      mkdirp = require('mkdirp'),
      path = require('path'),
      { URL } = require('url'),
      commander = require('commander');

(function main(argv) {

  let countryCode
  let outputPath
  let tmpPath

  commander
    .usage('<country-code> [output-path] [tmp-path]')
    .arguments('<country-code> [output-path] [tmp-path]')
    .action((code, output, tmp) => {
      countryCode = code
      outputPath = output
      tmpPath = tmp
    })

  commander.on('--help', () => {
    console.log('')
    console.log('  Examples:')
    console.log('')
    console.log('    $ gadm2geojson ARG ../../data/geojson')
    console.log('    $ gadm2geojson ARG ../../data/geojson /tmp/geojson')
    console.log('')
  })

  commander.parse(argv)

  if(countryCode === undefined) {
    commander.outputHelp()
    process.exit(1)
  }

  outputPath = outputPath || `${process.env.DATA_PATH || '/data'}/geojson`;
  tmpPath = tmpPath || '/tmp';

  // GADM 4.0 let baseUrl = 'http://geodata.ucdavis.edu/data/gadm4.0/kmz/'
  let baseUrl = 'http://biogeo.ucdavis.edu/data/gadm2.8/kmz/'
  console.log(`> Country code: ${countryCode}`)
  console.log(`> Output path: ${outputPath}`)
  console.log(`> Temporary path: ${tmpPath}`)
  console.log(`> Downloading GADM data in KMZ format from ${baseUrl}`)

  console.info(`> Starting ... ${countryCode}`)

  const config = {
    path: {
      out: outputPath,
      tmp: tmpPath
    },
    url: {
      base: baseUrl
    }
  }

  Promise.resolve()
    .then(() => processCountry(countryCode, 0, config))
    .then(() => processCountry(countryCode, 1, config))
    .then(() => console.log("> All done!!"))
    .catch((err) => console.error(`> ERROR: ${err}`))
})(process.argv)

/******************************************************************************
 * Aux functions
 */
const extractZipContentAt = function(kmlFilename, outPath) {
  return function(zipFile) { // path included
    return new Promise(function(resolve, reject) {

      const zip = new AdmZip(zipFile)
      const zipEntry = zip.getEntry(kmlFilename)

      if(!zipEntry) {
        return reject("Expected KML filename not found in zip file.")
      }

      zip.extractEntryTo(zipEntry, outPath, /*maintainEntryPath*/true, /*overwrite*/true)
      resolve(path.join(outPath, kmlFilename))
    })
  }
}

const toGeoJsonAt = function(geojsonFilename, outPath) {
  return function(kmlFilename) { // path included
    return new Promise(function(resolve, reject) {

      const convert = function(data) {
        //https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/JSON/stringify
        return JSON.stringify(tj.kml(xmldom.parseFromString(data.toString())), null, 2 /* 4 */);
      }

      mkdirp(outPath, function(err) {
        if(err) {
          return reject(err)
        }

        const outPathFile = path.join(outPath, geojsonFilename)
        const geoJsonData = convert(fs.readFileSync(kmlFilename, 'utf8'))

        fs.writeFile(outPathFile, geoJsonData, 'utf8', (err) => {
          if(err) {
            return reject(err)
          }
          resolve(outPathFile)
        })
      })
    })
  }
}

const downloadAt = function(url, kmzFilename, outPath) {
  return new Promise(function(resolve, reject) {

    mkdirp(outPath, function(err) {
      if(err) {
        return reject(err)
      }

      const outPathFile = path.join(outPath, kmzFilename)
      const file = fs.createWriteStream(outPathFile)

      request
        .get(url)
        .on('response', function(response) {
          if (response.statusCode !== 200) {
            return reject('Response status was ' + response.statusCode)
          }
        })
        .on('error', function (err) {
          fs.unlink(outPathFile);
          return reject(err.message)
        })
        .pipe(file)

      file.on('finish', function() {
        file.close(function() {
          resolve(outPathFile)
        })
      });

      file.on('error', function(err) {
        fs.unlink(outPathFile)
        return reject(err.message)
      })
    })
  })
}

const processCountry = function(code, level, config) {
  console.info(`- Processing country ${code} with level ${level}`)

  // For GADM 4.0 the filename template changes to `gadm40_${code}_${level}`
  const filename = `${code}_adm${level}`
  const kmzFilename = `${filename}.kmz`
  const kmlFilename = `${filename}.kml`
  const geojsonFilename = `${filename}.geojson`

  const url = new URL(kmzFilename, config.url.base)
  console.log(`Downloading ${url}`)
  const outPath = path.join(config.path.out, code)

  return downloadAt(url.href, kmzFilename, config.path.tmp)
          .then(extractZipContentAt(kmlFilename, config.path.tmp))
          .then(toGeoJsonAt(geojsonFilename, outPath))
          .then((geojsonFile) => console.info(`  -> GeoJson file successfuly created at: ${geojsonFile}`))
}


