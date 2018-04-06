const fs = require('fs'),
      argv = require('minimist')(process.argv.slice(2)),
      request = require('request'),
      //extract = require('extract-zip'),
      AdmZip = require('adm-zip'),
      xmldom = new (require('xmldom').DOMParser)(),
      tj = require('togeojson'),
      mkdirp = require('mkdirp'),
      path = require('path'),
      { URL } = require('url');

(function main(argv) {

  const countryCode = argv._[0]
  console.info(`# Starting ... ${countryCode}`)

  const config = {
    path: {
      out: argv._[1] || '/output',
      tmp: argv._[2] || '/tmp'
    },
    url: {
      base: 'http://biogeo.ucdavis.edu/data/gadm2.8/kmz/'
    }
  }

  Promise.resolve()
    .then(() => processCountry(countryCode, 0, config))
    .then(() => processCountry(countryCode, 1, config))
    .then(function() {
      console.log("# All done!!")
    })
    .catch(function(err) {
      console.error(`ERROR: ${err}`)
    })
})(argv)

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

const download = function(url, dest, cbSuccess, cbError) {
  return new Promise(function(resolve, reject) {
    const file = fs.createWriteStream(dest)

    request
      .get(url)
      .on('response', function(response) {
        if (response.statusCode !== 200) {
          return reject('Response status was ' + response.statusCode)
        }
      })
      .on('error', function (err) {
        fs.unlink(dest);
        return reject(err.message)
      })
      .pipe(file)

    file.on('finish', function() {
      file.close(function() {
        resolve(dest)
      })
    });

    file.on('error', function(err) {
      fs.unlink(dest)
      return reject(err.message)
    })
  })
}

const processCountry = function(code, level, config) {
  console.info(`- Processing country ${code} with level ${level}`)

  const filename = `${code}_adm${level}`
  const kmzFilename = `${filename}.kmz`
  const kmlFilename = `${filename}.kml`
  const geojsonFilename = `${filename}.geojson`

  const url = new URL(kmzFilename, config.url.base)
  const downloadPath = path.join(config.path.tmp, kmzFilename)
  const outPath = path.join(config.path.out, code)

  return download(url.href, downloadPath)
          .then(extractZipContentAt(kmlFilename, config.path.tmp))
          .then(toGeoJsonAt(geojsonFilename, outPath))
          .then((geojsonFile) => console.info(`  -> GeoJson file successfuly created at: ${geojsonFile}`))
}


