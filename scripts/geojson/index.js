const fs = require('fs')
const argv = require('minimist')(process.argv.slice(2))
const request = require('request')
const extract = require('extract-zip')
const AdmZip = require('adm-zip')

const tj = require('togeojson')
const path = require('path')
const { URL } = require('url');


// const extractZipContentAt = function(outPath) {
//   return function(zipFile) { // path included
//     return new Promise(function(resolve, reject) {
//       extract(zipFile, {dir: outPath}, function(err) {
//         if(err) {
//           reject(err)
//         }
//         resolve(outPath)
//       })
//     })
//   }
// }

const extractZipContentAt = function(kmlFilename, outPath) {
  return function(zipFile) { // path included
    return new Promise(function(resolve, reject) {

      const zip = new AdmZip(zipFile)
      const zipEntry = zip.getEntry(kmlFilename)

      if(!zipEntry) {
        reject("Expected KML filename not found in zip file.")
      }

      zip.extractEntryTo(zipEntry, outPath, /*maintainEntryPath*/true, /*overwrite*/false)
      resolve(kmlFilename)
    })
  }
}

const toGeoJsonAt = function(outPath) {
  return function(kmlFilename) { // path included
    return new Promise(function(resolve, reject) {

      // TODO: convert to geojson
      resolve(kmlFilename)

    })
  }
}

var download = function(url, dest, cbSuccess, cbError) {

  return new Promise(function(resolve, reject) {
    var file = fs.createWriteStream(dest)

    request
      .get(url)
      .on('response', function(response) {
        if (response.statusCode !== 200) {
          reject('Response status was ' + response.statusCode)
        }
      })
      .on('error', function (err) {
        fs.unlink(dest);
        reject(err.message)
      })
      .pipe(file)

    file.on('finish', function() {
      file.close(function() {
        resolve(dest)
      })
    });

    file.on('error', function(err) {
      fs.unlink(dest)
      reject(err.message)
    })
  })
}


const processCountry = function(code, level, config) {
  console.info(`Processing country ${code} with level ${level}`)

  const filename = `${code}_adm${level}`
  const kmzFilename = `${filename}.kmz`
  const kmlFilename = `${filename}.kml`

  const url = new URL(kmzFilename, config.url.base)
  const downloadPath = path.join(config.path.tmp, kmzFilename)

  download(url.href, downloadPath)
    .then(extractZipContentAt(kmlFilename, config.path.tmp))
    .then(toGeoJsonAt(config.path.out))
    .then(function() {
      console.log("All done!!")
    })
    .catch(function(err) {
      console.error(`ERROR: ${err}`)
    })
}

//
let countryCode = argv._[0]
console.info(countryCode)

const config = {
  path: {
    out: path.join(argv._[1] || '/output', countryCode),
    tmp: argv._[2] || '/tmp'
  },
  url: {
    base: 'http://biogeo.ucdavis.edu/data/gadm2.8/kmz/'
  }
}

processCountry(countryCode, 0, config)
processCountry(countryCode, 1, config)

